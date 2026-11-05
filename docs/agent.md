# Python Agent 说明

本文展开 `services/agent`：**原理、一轮对话怎么走、图上的节点和边、Tool、意图、记忆表谁读谁写**。

契约（意图枚举、SSE 事件名）以 [`conversation.md`](./conversation.md) 为准；internal 路径以 [`api-internal.md`](./api-internal.md) 为准；表字段以 [`data-model.md`](./data-model.md) 为准。本文只解释运行时怎么流，冲突时先改那些文档再改本文。

---

## 1. 它在系统里是什么

Agent 是 **内网对话运行时**，不是第二套 App 后端。

| 它做 | 它不做 |
|---|---|
| 意图路由、抽槽、指代、话术 | 持有联盟密钥 / PID / relationId |
| 按白名单调 Java Tool | 对 APP 或公网提供 API |
| 工作记忆（Checkpointer） | 转链、deeplink、校验 APP 是否安装 |
| 读语义记忆、高置信时提议写入 | 登录、SSO、Flyway 迁表（表结构归 Java） |
| SSE 事件名与 Java 对齐后吐出 | 编造物流、佣金数字、平台政策 |

调用关系：

```
Android/iOS/H5  --JWT SSE-->  shop-api-app  --内部令牌 SSE-->  Agent (LangGraph)
                              ^                            |
                              |     Tool HTTP              |
                              +----- /internal/v1/**  <----+
```

Java 负责：鉴权、落用户消息、超时、把 Agent 的 SSE **转发给** Android / iOS / H5。Agent 只认 `X-Internal-Token`，并用 Java 注入的 `userId` / `conversationId` 当租户边界。不感知上帝视角（Java 用同一 userId 调图）。

技术锁定见 `AGENTS.md` §2.2：FastAPI + LangGraph + Checkpointer Postgres + httpx + Langfuse。

---

## 2. 原理（为什么用一张图，而不是多 Agent）

导购、查单、授权、工单 **共用同一会话、同一指代、同一登录用户**。拆成多个 Agent 进程会：

- 「刚才那个订单」对不齐；
- 多 hop 延迟和费用；
- SSE 被两套生成器抢。

因此锁定 **一张 `StateGraph`，子图是同进程节点包**。只有「去购买」离开对话，走 Java `POST /api/app/v1/goods/convert-link`，不进图。

工业界对应做法：

- **分层意图**（`shop.search` 而不是一个 `shopping`）减少工具误用；
- **结构化输出**（intent + slots + confidence），低置信不调搜品；
- **工具接地**：数字与商品只来自 Java；
- **HITL**：授权/取消授权用 LangGraph `interrupt`，用户点卡片后再 `resume`，不是模型假装已授权。

LLM 允许做的事只有四类：**路由、抽槽、指代、话术**。其余都是代码节点或 Tool。

---

## 3. 一轮对话的流程

### 3.1 入口（生成与 SSE 解耦）

1. Android/iOS `POST /api/app/v1/chat/turns`（JSON）。Java 校验 JWT、幂等 `clientMsgId`、写入用户消息、分配 `runId`，**立刻返回**。此时图可以还没开始。
2. 任一 app 副本上的 **Worker** 调 Agent `POST /internal/v1/graph/invoke`（SSE），把每条事件 `XADD` 到 Redis Stream `chat:run:{runId}`。**不要**把 Agent 的流直接写给 APP。
3. Android/iOS `GET /api/app/v1/chat/runs/{runId}/events`。SSE 泵每 ≤100ms 从 Redis 读出 `Last-Event-ID` 之后的事件推给客户端。
4. 刷新或闪断：**不取消 Worker**。重连带上一条 `id:`，从 Redis 续传。换到另一副本同样可读。
5. `done` 后 Java 落 assistant 消息；Stream 再保留 30 分钟。

`hitl` 非空的 POST 开 **新 run**，Worker 从 checkpoint resume，事件进新的 Redis 键。

### 3.1.1 用户点「停止」

LangGraph **没有**跨进程的 stop API。能停的是「正在跑 `astream` 的那条 asyncio 任务」，而任务在 **Worker 所在机器** 上。因此：

```
Android/iOS  POST /chat/runs/{runId}/cancel
     → 任一副本 SET Redis chat:run:{runId}:cancel
     → 跑图的 Worker 发现标志 → abort 调 Agent 的 HTTP
     → FastAPI 感知断开 → task.cancel() → astream 抛 CancelledError
     → Worker XADD cancelled（终态）
```

节点里 **不要**靠 LLM 自己决定停。也不要用 `interrupt()`（那是授权确认）。协作式检查（每个 node 开头读 cancel）只能当补充，**预打断 HTTP** 才能停住正在流式输出的模型。

取消后 checkpoint 保持上一完整节点；同一会话下一句新开 run 即可。刷新页面绝不走 cancel。

### 3.1.2 积分不足

扣费在 **Java Worker**：Agent 只发 SSE `usage`。Java 按 Nacos `credit.tokens-per-credit` 做 `round(tokens / rate, 2)`（HALF_UP，有消耗则最少 0.01）写 `llm_usage` + `credit_ledger`。余额为字符串两位小数。不足则 abort HTTP，再 `credit_card` + `credit_exhausted`。不要 `interrupt()` 买积分。支付不进图。

### 3.1.3 提示词与模型从哪来

Worker 开 run 前从库读取，放进 `POST /internal/v1/graph/invoke`（见 [`api-internal.md`](./api-internal.md)）：

- `prompts`：`agent_prompt` 八个固定 key 的正文。Agent 按当前节点取对应 key，不在仓库里再藏一套会盖过数据库的提示词。
- `llm`：按 `llm_model.weight` 在**本 run** 抽中的一条。本轮每一次模型调用都用它。`usage.model` 等于该条 `model_name`。

`test` / `prod` 没有可分流模型，或提示词缺行：不调用本服务。仅 `local` 且模型表没有候选时，`llm` 为空，此时才读进程环境变量 `OPENAI_*`。密钥不写日志、不写 Langfuse。

Langfuse 继续做 trace。提示词正文的修改入口是管理后台，不是 Langfuse Prompt。

### 3.2 图总览

```mermaid
flowchart TD
  start([invoke]) --> ingest
  ingest --> memLoad[memory_load]
  memLoad --> router[intent_router]
  router -->|confidence 低于 0.6| clarify
  clarify --> reply[reply_sse]
  router -->|pending 中取一条| router
  router -->|shop.*| shop[subgraph_shop]
  router -->|me.* / auth.*| account[subgraph_account]
  router -->|order.query| orderSg[subgraph_order]
  router -->|earn.invite| earn[subgraph_earn]
  router -->|kb.faq / ticket.create / handoff| support[subgraph_support]
  router -->|chitchat| chitchat
  shop --> hitlGate{需要 HITL?}
  account --> hitlGate
  orderSg --> reply
  earn --> reply
  support --> reply
  chitchat --> reply
  hitlGate -->|auth.grant / auth.revoke 未确认| hitl[interrupt]
  hitl --> wait([等用户 hitl 恢复])
  hitlGate -->|否| memWrite[memory_write]
  shop --> memWrite
  account --> memWrite
  orderSg --> memWrite
  earn --> memWrite
  support --> memWrite
  chitchat --> memWrite
  memWrite --> reply
  reply --> end([done])
```

HITL 恢复后从 `hitl` 节点继续：调 `start_authorization` / `revoke_authorization`，再 `memory_write` → `reply_sse`。

### 3.3 单轮单主意图

用户一句里两件事（「这耳机怎么样，那单怎么还没同步」）：

- `primaryIntent` 本轮执行；
- 其余进工作记忆 `pendingIntents[]`；
- `done` 后话术可提一句「订单的事下一句再说」，或下一轮 `ingest` 优先弹出 pending。

禁止两个 subgraph 并行写同一条 SSE。

---

## 4. 状态（AgentState）

建议字段（实现时用 TypedDict / Pydantic，名称可微调，语义锁定）：

| 字段 | 谁写 | 含义 |
|---|---|---|
| `user_id` / `conversation_id` / `trace_id` | ingest | 租户与追踪 |
| `input_type` / `text` / `source_url` / `image_object_key` | ingest | 本轮输入 |
| `hitl` | ingest | 恢复载荷 |
| `installed_platforms` / `authorized_platforms` | ingest | Java 注入，搜品过滤 |
| `intent` / `confidence` / `slots` | intent_router | 一级意图 |
| `pending_intents` | intent_router | 本轮没跑的意图 |
| `referents` | coref | 绑定的 goodsId / orderId |
| `clarify_question` / `missing_slots` | slot_fill / clarify | 追问 |
| `goods_cards` | shop 工具节点 | 本轮要下发的卡 |
| `ticket_id` | support | 新建工单 |
| `sse_buffer` | 各节点 | 待 `reply_sse` 刷出的事件 |
| `interrupt_id` | hitl | 与 APP 卡片对应 |

Checkpointer 持久化上述与会话相关的部分；**不要**把 embedding 全量放进 state。

---

## 5. 节点（Nodes）

每个节点尽量纯函数 + 显式 I/O，便于单测。禁止在节点里 `new` 联盟 SDK。

| 节点 | 文件 | 做什么 | 副作用 |
|---|---|---|---|
| `ingest` | `nodes/ingest.py` | 规范化输入；`hitl` 则设置 resume 标志 | 无库 |
| `memory_load` | `nodes/memory_load.py` | 读工作记忆（checkpoint 已自动 hydrate）；按需读语义/情景 | 只读 PG / Tool |
| `intent_router` | `nodes/intent.py` | LLM 结构化输出 intent+slots+confidence | 无库 |
| `coref` | `nodes/coref.py` | 「这个」「上次那种」→ goodsId/orderId | 只读 state + 近况事件 |
| `slot_fill` | `nodes/slot_fill.py` | 对照意图槽位表，缺则转 clarify | 无 |
| `clarify` | `nodes/clarify.py` | 生成 `clarify` 事件，**不调** search_goods | 无 |
| `subgraph_shop` | `subgraphs/shop.py` | 见 §5.1 | Tool HTTP |
| `subgraph_account` | `subgraphs/account.py` | 见 §5.2 | Tool / interrupt |
| `subgraph_order` | `subgraphs/order.py` | `list_orders` / `get_order` | Tool |
| `subgraph_earn` | `subgraphs/earn.py` | `get_affiliate_stats` | Tool |
| `subgraph_support` | `subgraphs/support.py` | `search_kb` / `create_ticket` | Tool |
| `chitchat` | 可并入 `nodes/chitchat.py` | 短回复，无 Tool | 无 |
| `hitl` | `nodes/hitl.py` | `interrupt`；resume 后调授权 Tool | Tool |
| `memory_write` | `nodes/memory_write.py` | 更新槽位、卡片 id、候选偏好 | checkpoint 自动存；语义/事件按 §8 |
| `reply_sse` | `nodes/reply_sse.py` | 按序 yield SSE | 无库 |

### 5.1 导购子图 `subgraph_shop`

```
slot_ok? --否--> clarify（子图内或退回全局 clarify）
     --是--> 按 intent 选 Tool
shop.search / gift / scene --> search_goods（可多次，场景清单循环）
shop.link  --> parse_goods_link（恰好一张卡）
shop.image --> parse_goods_image --> 必要时 search_goods
shop.compare --> 对 referents 调 goods_detail（不海搜）
     --> 把 GoodsCard 写入 state.goods_cards
```

`gift` / `scene` 在关键词仍空时必须先 clarify（对象、预算、年级），禁止用模型直接编 SKU。

### 5.2 账户子图 `subgraph_account`

| intent | 节点行为 |
|---|---|
| `me.profile` | `get_me` + 读 `memory_semantic` / preference 摘要，话术「你了解我多少」只陈述工具结果 |
| `me.auth_list` | `list_authorizations` |
| `me.stats` | `get_order_stats` + `get_wallet`，金额只复述接口 |
| `auth.grant` | 未 resume：`start_authorization` 得 `authUrl` → interrupt（`auth_card`） |
| `auth.revoke` | 未 resume：`confirm_card` interrupt；confirm 后 `revoke_authorization` |

---

## 6. 边（Edges）

边全部是 **条件边（conditional edges）**，由 state 决定，不要靠 LLM 再选下一跳。

| 从 | 条件 | 到 |
|---|---|---|
| `ingest` | `hitl` 有值 | `hitl`（resume） |
| `ingest` | 否则 | `memory_load` |
| `memory_load` | 恒定 | `intent_router` |
| `intent_router` | `confidence < 0.6` | `clarify` |
| `intent_router` | `intent` 前缀 `shop.` | `subgraph_shop` |
| `intent_router` | `me.*` 或 `auth.*` | `subgraph_account` |
| `intent_router` | `order.query` | `subgraph_order` |
| `intent_router` | `earn.invite` | `subgraph_earn` |
| `intent_router` | `kb.faq` / `ticket.create` / `handoff` | `subgraph_support` |
| `intent_router` | `chitchat` | `chitchat` |
| `clarify` / 各 subgraph 结束 | 需要授权类 interrupt | `hitl` |
| 各 subgraph / `chitchat` / `hitl` 完成 | 恒定 | `memory_write` |
| `memory_write` | 恒定 | `reply_sse` |
| `reply_sse` | 恒定 | END |

实现：`builder.py` 里 `add_conditional_edges("intent_router", route_intent, {...})`。`route_intent` 必须是无 I/O 的纯函数，单测覆盖每条 intent。

---

## 7. 工具（Tools）

全部经 `tools/java_client.py`：`httpx`、超时（建议单次 5s）、header `X-Internal-Token` + `X-User-Id`（**必须等于** ingest 的 userId）。**禁止** Tool 去调 `/api/app/v1/chat/turns` 或 Agent 自己。

路径与白名单以 [`api-internal.md`](./api-internal.md) 为准，这里按 **谁在图里调用** 归类：

| Tool | 调用节点 | 读/写 Java 侧 | 失败时 |
|---|---|---|---|
| `search_goods` | shop | 读 Redis/联盟，可写瘦快照；**>6 条** Java 建合集，返回 `collectionId` + 预览 ≤4 | SSE `error` 或话术「暂时搜不到」，禁止编商品；溢出发 `collection_card` 禁止再刷卡 |
| `parse_goods_link` | shop | 解析 URL + 详情缓存 | 无法解析则 clarify「链接打不开」 |
| `parse_goods_image` | shop | OCR 在 Java | 识别空则追问 |
| `goods_detail` | shop.compare | 只读缓存 | 无缓存则短句失败 |
| `get_me` | account | 读 `app_user` + preference | |
| `list_authorizations` | account | 读 `platform_authorization` | |
| `start_authorization` | hitl | 写 pending 授权、返回 URL | |
| `revoke_authorization` | hitl | 更新授权状态 | 必须已 confirm |
| `list_orders` / `get_order` | order | 读 `app_order` | `logistics=null` 就说未知；返佣只复述 `userCommission`，不提媒体佣金 |
| `get_order_stats` | account | 聚合订单 | |
| `get_wallet` | account | 读 `wallet` / 流水汇总 | |
| `get_affiliate_stats` | earn | 读 `app_invite` + 分佣汇总 | |
| `search_kb` | support | 读 `kb_article`（后期 MCP 只换这里） | **miss 必须显式**，引导建单 |
| `create_ticket` | support / handoff | 写 `ticket` | |
| `search_memory` | memory_load（可选） | 读 `memory_semantic` | 也可 Agent 只读连 PG |

导购意图 **禁止** 调转链、改钱包。`kb.faq` **禁止** `search_goods`。

---

## 8. 意图（运行时）

路由用 **结构化输出**（JSON schema / tool calling），枚举必须是 [`conversation.md`](./conversation.md) 那张表，禁止模型自由发挥新 intent。未知 → `chitchat` 或 `handoff`，不要静默当 `shop.search`。

置信度：

- `≥ 0.6`：进对应子图；
- `< 0.6`：`clarify`（「你是想买东西，还是查已买的订单？」），本轮零 Tool。

槽位未填：子图内 clarify，例如纸巾缺 `category`。

指代：`coref` 在 router **之后**、Tool **之前**。优先本轮/上轮 `goods_cards`，再 checkpoint 里的 last item，再 `user_event` 近况。绑不上就 clarify，不要猜一个 item_id 去搜。

---

## 9. 记忆：表怎么读、怎么写

三层不要理解成三张一样的 KV。

### 9.1 Working（会话）— Checkpointer，无业务表

| | |
|---|---|
| 存储 | LangGraph `PostgresSaver`（库自带 checkpoint 表） |
| 键 | `thread_id = conversation.id` |
| 读 | 每轮图启动时框架自动加载；`memory_load` 只把槽位/pending/本轮卡片投影进 prompt |
| 写 | 每轮节点结束后框架自动 checkpoint；`memory_write` 负责把 `slots`、`pending_intents`、`goods_cards[].goodsId`、`interrupt_id` 放进 state |
| 谁维护表结构 | LangGraph 迁移/官方表，**不要**为 working 再建 `memory_item` |

Java **不要**每轮把全量向量塞进 invoke body。

### 9.2 Episodic（事件）— `user_event`

| 操作 | 何时 | 谁执行 |
|---|---|---|
| 写 `browse` | 用户打开详情成功 | **Java** `IGoodsService`（不经 Agent） |
| 写 `click` | `goods/convert-link` 成功 | **Java**（同时写 `click_event`） |
| 写 `order` | 订单同步归因到用户 | **Java** Quartz |
| 写 `auth_grant` / `auth_revoke` | 授权回调或 revoke 成功 | **Java** |
| 读 | 「上次那种」、过滤已买、订单指代 | Agent `memory_load` / `coref`：Tool 或只读 SQL（推荐经 Java 只读接口，避免 Agent 写事件表） |

Agent **默认不写** `user_event`，避免和 Java 双写打架。浏览 90 天清理也是 Java 任务。

### 9.3 Semantic（长期）— `app_user_preference` + `memory_semantic`

**`app_user_preference`（一用户一行，结构化）**

| 操作 | 谁 | 规则 |
|---|---|---|
| 读 | Agent `me.profile` / 导购过滤平台预算 | `get_me` 已带摘要，或直读 |
| 写 | **Java** 问卷接口；或 Agent 仅当用户明确「记住以后…」且 `confidence ≥ 0.8` | 经 Java upsert，Agent 不直接 UPDATE 钱包类列 |

**`memory_semantic`（开放句 + 向量）**

| 操作 | 谁 | 规则 |
|---|---|---|
| 读 | `memory_load`：本轮 query embedding，**先 `user_id` 过滤再** ANN | `valid_to IS NULL` |
| 写 inferred | `memory_write` 从闲聊抽取 | `confidence < 0.8` → 候选，不覆盖 survey |
| 写 explicit | 用户说「记住…」 | `source=explicit`，可 `valid_to` 旧相反记忆 |
| 写 survey | 画像问卷 | **只 Java** |

客服/查账/工单路径：**默认不写** semantic。矛盾句（「以后别推荐海鲜」）先把旧 `kind=hate` 相反记录 `valid_to=now()`，再插新行。

### 9.4 对话落库（给运营抽检，不是工作记忆）

| 表 | 读 | 写 |
|---|---|---|
| `conversation` | Java 列表；Agent 一般不碰 | Java 创建/更新 `last_intent`（来自 `done.intent`） |
| `conversation_message` | Java / Admin | Java：用户消息在 invoke 前；助手消息在 SSE `done` 后（payload 含卡、工单号） |

Agent 不自己 INSERT 这两张表，避免和幂等 `clientMsgId` 冲突。

### 9.5 业务表与 Agent 的关系（只读/经 Tool）

| 表 | Agent |
|---|---|
| `app_user` | 经 `get_me` 读 |
| `platform_authorization` | 经 list/start/revoke |
| `app_order` | 经 orders Tool，不写 |
| `wallet` / `wallet_ledger` | 经 `get_wallet` 读，**禁止** Agent 调提现 |
| `withdrawal` | 对话 P0 不走；「我的」页走 Java |
| `app_invite` | 经 `get_affiliate_stats` |
| `ticket` | 经 `create_ticket` 写，读回执号 |
| `kb_article` | 经 `search_kb` |
| `goods_snapshot` | 经搜品/详情 Tool，Agent 不直写 |

---

## 10. SSE：节点如何对应事件

`reply_sse`（或子图内提前 flush）按下面顺序推，名称锁定：

| 时机 | event |
|---|---|
| router 完成后 | `intent` |
| 缺槽 / 低置信 | `clarify` |
| 导购有卡 | ≤6：`product_card` / `product_cards`；>6：仅 `collection_card` |
| HITL 授权 | `auth_card` 后 interrupt（连接可保持或结束，恢复靠下一 POST） |
| HITL 取消授权 | `confirm_card` |
| 建单成功 | `ticket_created` |
| 话术 | `text_delta`（可与工具并行：先字后卡，或先卡后字，F01 建议 **先 intent，再 text，再卡**） |
| 异常 | `error` |
| 正常结束 | `done` |

Langfuse：每个 invoke 一个 trace，`traceId` 与 Java 相同；每个节点/Tool 一个 span。

---

## 11. HITL 时序（授权）

```
用户「淘宝怎么授权」
  → router auth.grant
  → start_authorization
  → SSE auth_card { authUrl, interruptId }
  → graph interrupt，checkpoint 保存
用户在端内完成 OAuth
  → 联盟回调 Java → platform_authorization=authorized → 可选插入 system 消息
  → APP 再 POST turns，hitl.action=authorized
  → Agent resume → 话术「授权完成」→ done
```

取消授权：先 `confirm_card`，`action=confirm` 才 `revoke_authorization`。

---

## 12. 测试与观测

- `tests/test_intent.py`：`route_intent` 纯函数、低置信走 clarify、白名单外 intent 拒绝。
- 工具节点用 httpx mock，不断真联盟。
- `handoff` / 无订单：断言话术不含编造物流。
- Langfuse 关闭时（`LANGFUSE_ENABLED=false`，仅非 prod）图仍能跑。

---

## 13. 和 Java / APP 的边界（再强调）

- 点商品卡进详情、去购买、是否安装 APP：只走 `/api/app/v1/goods/*`，**不进图**。
- 知识库以后换 MCP：只替换 `search_kb` 的实现，节点和边不变。
- 不引入独立客服 Agent、不引入 Letta/Mem0 当主存储。
