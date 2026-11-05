# Research: 005-agent

未知项是：检查点库哪一版能配上已锁定的图、本地固定问候怎样保留、当次请求怎样送到助手、长期偏好写到哪、追踪用哪一版 SDK。意图和事件名不在这里重选。

## 检查点用 2.0.25，不用 Store

- Decision: 工作记忆使用 `langgraph-checkpoint-postgres==2.0.25` 的 `PostgresSaver` / `AsyncPostgresSaver`。连接要求 `autocommit=True` 与 `row_factory=dict_row`。进程启动时调用一次 `setup()`，表不进 Flyway。不使用该包的 `PostgresStore`。
- Rationale: `langgraph==0.2.70` 要求 `langgraph-checkpoint>=2.0.10,<3`。`2.0.25` 是这条主版本的最后一版，依赖 `langgraph-checkpoint>=2.1.2,<3`，许可证 MIT。3.x 需要 checkpoint 4.x，会把已锁定的图抬走。该包 3.1.1 之前的 Store 命名空间检索有越权读取问题；本功能不调用 Store，工作记忆按 `thread_id` 等值读写，不走那条检索。
- Alternatives considered:
  - `langgraph-checkpoint-postgres` 3.1.3：修了 Store 问题，但配不上 `langgraph` `0.2.70`。
  - 自己建 `memory_item`：权威文档禁止再为工作记忆建业务表。
  - 内存检查点：刷新后槽位和未完成的授权会丢。

## 图只使用当次注入的模型和八条提示词

- Decision: 每一轮用 `langchain-openai` `0.3.3` 建一个 OpenAI 兼容客户端，地址、模型名、密钥和超时都来自当次 `llm`。同一轮后续调用复用这一个客户端。`llm` 为空时，仅本地读取进程环境变量 `OPENAI_*`。测试和生产拒绝空模型。八个提示词键缺一或正文为空时，购物服务不发起调用。仓库和 Langfuse 都不存放会盖过数据库的正文。密钥不写入日志和追踪。
- Rationale: 运营改提示词和权重后，下一轮必须生效。进程里留一份默认正文会盖过数据库。Langfuse `2.57.0` 的追踪集成是 MIT，且已锁定；不升级到需要另一套回调的主版本。
- Alternatives considered:
  - 仓库里放一份备用提示词：规格禁止。
  - 引入 DeepSeek 专用 SDK：权威文档指定走 OpenAI 兼容协议。
  - 把密钥放到追踪的输入里：规格要求出现次数为 0。

## 本地连得上助手就走图，连不上才回固定问候

- Decision: 内网网关在 local、test、prod 都装配。test 和 prod 的工人始终调用助手，失败写现有的助手不可用终态。local 的工人先调用同一网关。只有连接被拒绝，或在响应头之前超时，才写现有的固定问候和 `chitchat` 终态。已经拿到 HTTP 状态时，4xx 和 5xx 写助手不可用，不用问候掩盖。助手一旦返回事件，就按真实事件转发；之后失败的终态是错误或取消，不用问候覆盖已经转发的内容。
- Rationale: 今天 local 根本不装配网关，所以助手写好了也收不到发言。规格要求接上助手后走真实意图，同时允许购物服务单独启动。两条工人不能同时标成 local，否则容器里会有两个实现。
- Alternatives considered:
  - local 始终调用助手：没启动助手时，开发机开一轮就失败。
  - 继续只在 test/prod 调助手：本地无法验收「我要买纸巾」。

## 请求体按已有契约组装，并在读取时响应取消

- Decision: 用 Jackson 组装 `POST /internal/v1/graph/invoke`。字段与 [`docs/api-internal.md`](../../docs/api-internal.md) 一致，模型名使用 `model`，不再发送 `modelName`。开一轮按输入类型校验：文本和语音转写必须有正文，商品链接必须有链接，商品图必须有图片键。`hitl` 非空时允许没有正文，确认动作以 `hitl` 为准。补上现有开一轮接口还没往下传的 `inputType`、`sourceUrl`、`imageObjectKey`、`hitl`、已安装平台，并由购物服务填入该用户的已授权平台。连接超时保持 3 秒。整段流的截止时间用 `chat.run-timeout-seconds`，不再把整个请求限制成 5 秒。读取事件的循环在每条事件前后查看取消标记；看到后关闭响应流，让助手任务取消。助手侧用任务取消结束 `astream`，不使用图的暂停。
- Rationale: 现有网关把正文拼成字符串，提示词里的引号和换行会破坏 JSON，也丢了链接、图片和授权恢复。5 秒上限会在话术还没写完时切断。取消标记已经存在，缺的是把正在读的流关掉。
- Alternatives considered:
  - 助手自己轮询取消标记：标记在购物服务的 Redis，助手不应再持有一套取消协议。
  - 用图的 `interrupt()` 表示停止：规格禁止，那是授权确认。

## 用量只上报，扣费仍在购物服务

- Decision: 助手每完成一次模型调用就发送一条 `usage`，模型名等于本轮抽中的那一个。`promptTokens`、`completionTokens`、`totalTokens`、`credits` 都是本轮累计。应扣金额是现有公式作用于本轮累计 token 的结果，减去本轮已经扣过的金额。`0.01` 最低扣费不按每一小段另算。同一累计值再次到达不扣第二次。`done` 时按最终用量多退少补，余额不为负。余额不够则掐断调用并写购积分终态。助手不调用任何改积分或支付的路径。开跑前余额不足的拦截保持现状。异常日志不打印调用正文。
- Rationale: 扣费规则已经写在 [`docs/conversation.md`](../../docs/conversation.md)。今天只有开跑前的拦截，因为之前没有真实用量。助手开始发 `usage` 之后，这段规则才有事件可扣。
- Alternatives considered:
  - 助手直接改积分账户：宪章禁止。
  - 只转发 `usage`、仍不扣：真实对话会绕过已写明的扣费。

## 长期偏好经内网写入，不在助手里拼 SQL

- Decision: 补上已有 `POST /internal/v1/memory/search` 的实现，只返回当前用户、`valid_to` 为空的行，按时间倒序，最多 20 条。新增 `POST /internal/v1/memory`，仅供记忆节点调用，不交给模型自由挑选。写入时用户编号取请求头，主键由购物服务生成。`source` 只接受 `explicit` 和 `inferred`。把握低于 0.8 的推断只作候选，不把已有正式行的 `valid_to` 填上。用户明确说「记住」时写 `explicit`。矛盾的厌恶句先给旧行写上 `valid_to`，再插入新行。本功能不写 `embedding`，也不引入向量模型。客服、查账、工单路径不调用写入。
- Rationale: 工具白名单已经有检索，但实现固定返回空列表。「你了解我多少」需要读到已记住的话。写入若由助手直接执行 SQL，会在 Python 里复制主键、用户边界和覆盖规则。没有锁定的向量模型，本阶段用当前用户的近期原文，不假装做了近邻检索。
- Alternatives considered:
  - 助手用 psycopg 写 `memory_semantic`：省一个路径，但用户边界和主键会有第二套实现。
  - 调用 `PostgresStore`：带有命名空间越权问题，而且不是本仓库的表。
  - 本功能不写长期偏好：规格要求明确记住时要更新。

## 授权恢复从暂停点继续

- Decision: 第一轮在授权节点发出授权卡或确认卡后暂停，本轮结束。下一句对同一个会话线程提交恢复值，从暂停的下一行继续，调用开始授权或取消授权。这次恢复不进入意图路由，也不再发一张卡。确认取消时，取消工具只调用一次。停止生成仍然是掐断调用，不走这条暂停。
- Rationale: 已锁定的图在暂停之后，下一次若从入口再跑，会重新抽意图，或在线程仍暂停时再塞一份新状态。用户看到的应是授权完成或取消完成。`docs/agent.md` 的边表已改成同一句。
- Alternatives considered:
  - 第二轮从入口看到恢复载荷后再走进授权节点：会再识别一次意图，确认句可能被当成闲聊。
  - 用暂停实现停止：规格禁止。

## 空着的预算和平台沿用明确记忆

- Decision: 搜品时，这句话给出的预算和平台优先。这两项为空，且该用户有未过期的 `explicit` 记忆时，用那份记忆填上。`inferred` 候选不参与搜索。关键词为空仍然追问。
- Rationale: 只把「记住预算 200」写进库、下一句「买纸巾」却不带上，用户会觉得没有记住。候选记忆还没经用户确认，不能拿来收窄商品。
- Alternatives considered:
  - 本阶段只保存、不参与搜索：评审里已否掉。
  - 用候选记忆直接搜：可能把猜出来的限制加到用户没说过的购物上。

## 指代只使用本会话已经出现的对象

- Decision: 指代绑定本轮和检查点里上一轮的商品编号、订单编号。绑不上就追问。不新增 `user_event` 读取接口，助手也不读这张表。
- Rationale: 规格要求优先绑定已经下发的商品，绑不上就追问。事件表没有现成的只读工具。为了「上次那种」临时加接口，会让本功能多一条未在白名单里的读取。
- Alternatives considered:
  - 助手直接查 `user_event`：和「工具只走内网白名单」冲突。
  - 猜一个商品编号继续搜：规格禁止。

## 测试不依赖真模型和真联盟

- Decision: 路由测试不创建模型客户端。图测试注入一个按用例返回意图、槽位和把握的假模型，工具传输使用 `httpx.MockTransport`。追踪关闭时图仍能跑完。生产配置下追踪开关不为真时，调用直接失败，不进入图。
- Rationale: 真模型会让测试不稳定，也会把密钥带进测试。`httpx` 已锁定，其模拟传输不需要再引入一套 HTTP 测试库。
- Alternatives considered:
  - 用录制的真实模型响应做日常测试：响应一变，意图用例就坏。
  - 引入 `respx`：版本表里没有它。
