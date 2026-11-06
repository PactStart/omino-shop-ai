---

description: "Task list for 005-agent"
---

# Tasks: 对话助手

**Input**: Design documents from `/specs/005-agent/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/invoke.md, contracts/tools.md, quickstart.md

**Tests**: 计划写明用 pytest 和现有 JUnit 5 验收，宪章要求测不过不算完成。下面点名的测试先写、先失败，再实现。不引入 `respx`，工具用 `httpx.MockTransport`。不打真模型和真联盟。

**Organization**: 先改权威文档和依赖，再搭调用骨架。故事 1 是可单独演示的搜品。故事 2 到 4 都是 P1，按订单、停止、授权的顺序接。故事 5、6 是 P2。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件，且不依赖未完成任务）
- **[Story]**: 用户故事标签，仅用户故事阶段使用

## Path Conventions

- 助手：`services/agent/`
- 购物服务：`services/java/shop-api-app/src/main/java/com/omino/shopai/app/`
- 权威文档：`AGENTS.md` §2.2、`docs/api-internal.md`、`docs/conversation.md`、`docs/agent.md`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 依赖先登记，记忆写入先写入契约。此阶段不调用模型

- [X] T001 在 `AGENTS.md` §2.2 锁定 `langgraph-checkpoint-postgres` `2.0.25`。写明只用 `PostgresSaver` / `AsyncPostgresSaver`，不用该包的 Store。许可证 MIT，配 `langgraph` `0.2.70`。本任务不改 `services/agent/pyproject.toml`
- [X] T002 [P] 把 `specs/005-agent/contracts/tools.md` 的 `POST /internal/v1/memory` 写入 `docs/api-internal.md` 工具表。`source` 只接受 `explicit` 和 `inferred`，`survey` 拒绝。`confidence` 为 0 到 1。`supersedeId` 必须属于 `X-User-Id`。服务端填写用户编号、主键和创建时间，`embedding` 留空。检索路径保持 `POST /internal/v1/memory/search`，不新开第二个检索路径
- [X] T003 在 T001 之后改 `services/agent/pyproject.toml`：运行依赖加入已锁定的 `langgraph==0.2.70`、`langchain-core==0.3.29`、`langchain-openai==0.3.3`、`langfuse==2.57.0`、`langgraph-checkpoint-postgres==2.0.25`、`psycopg[binary]==3.2.4`，并把 `httpx==0.28.1` 从开发依赖改为运行依赖。不加入 LangSmith，不升级这些版本

**Checkpoint**: 版本表和内网契约已覆盖新依赖和新写入路径。代码还没装这些包

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 内网入口、检查点、开一轮字段和路由纯函数。所有故事共用

**⚠️ CRITICAL**: 用户故事开始前必须完成本阶段

- [X] T004 按 `specs/005-agent/plan.md` 的目录新增空模块：`services/agent/app/api/invoke.py`、`services/agent/app/graph/state.py`、`services/agent/app/graph/builder.py`、`services/agent/app/graph/route.py`、`services/agent/app/graph/nodes/`、`services/agent/app/graph/subgraphs/`、`services/agent/app/tools/java_client.py`、`services/agent/app/observability/langfuse_setup.py`。`services/agent/app/main.py` 保留 `GET /health`，进程只监听 `127.0.0.1`
- [X] T005 在 `services/agent/app/api/invoke.py` 实现 `POST /internal/v1/graph/invoke` 的鉴权。`X-Internal-Token` 必须等于 `AGENT_INTERNAL_TOKEN`。缺失或不符返回 401，不进入图。不接受消费者令牌
- [X] T006 在 `services/agent/app/graph/builder.py` 用 `AsyncPostgresSaver` 作为检查点。连接使用 `autocommit=True` 和 `dict_row`。启动时调用一次 `setup()`，表不进 Flyway。`thread_id` 等于 `conversationId`。不调用 `PostgresStore`
- [X] T007 [P] 先写会失败的 `services/agent/tests/test_intent.py`：`route_intent` 覆盖 `docs/conversation.md` 的每条点分意图；把握低于 0.6 去追问且不搜品；未知意图进入闲聊或加急工单，不当成 `shop.search`。此任务只加测试，不写通过实现
- [X] T008 在 `services/agent/app/graph/route.py` 实现无 I/O 的 `route_intent`，使 T007 通过。边与 `docs/agent.md` §6 一致。恢复授权不进入这个函数
- [X] T009 扩展 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/web/app/dto/TurnRequestDto.java`、`services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/chat/IChatTurnService.java`、`ChatTurnServiceImpl.java`、`ChatRun.java` 和 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/web/app/ChatController.java`。带上 `inputType`、`sourceUrl`、`imageObjectKey`、`hitl`、`installedPlatforms`。`text` / `voice_transcript` 且没有 `hitl` 时正文 trim 后 1–2000；`hitl` 非空时允许没有正文；`goods_url` 必须有 http(s) 链接；`image` 必须有图片键。同一 `conversationId` + `clientMsgId` 仍返回原来的 `runId`
- [X] T010 改 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/chat/AgentGatewayImpl.java`：local、test、prod 都装配；用 Jackson 组装正文；模型字段名是 `model`，不再发送 `modelName`；填入该用户的 `authorizedPlatforms`；连接超时 3 秒；整段流的截止时间用 `chat.run-timeout-seconds`，不再把整个请求限制成 5 秒。异常日志只留状态码和追踪号，不打印调用正文
- [X] T011 把 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/chat/FixtureChatWorkerImpl.java` 收成只负责写固定问候的方法。新增 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/chat/LocalChatWorkerImpl.java`：只有连接被拒绝或响应头之前超时，才写「你好」和 `chitchat` 终态。已经拿到 HTTP 状态时写助手不可用。已经转发过事件后，终态用错误或取消，不用问候覆盖。test 和 prod 仍只用 `ChatWorkerImpl`。更新 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/chat/ChatRunTest.java` 里对 profile 的断言
- [X] T012 在 `services/agent/app/tools/java_client.py` 封装白名单调用。每个请求带 `X-Internal-Token` 和 `X-User-Id`，后者等于本轮入口里的用户，调用方不能改成别人。单次超时 5 秒。不调用订单同步路径，不调用转链
- [X] T013 在 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/web/internal/InternalAccountController.java` 实现已有的 `POST /internal/v1/memory/search`：只返回当前用户、`valid_to` 为空的行，按时间倒序，`limit` 缺省 20、最大 20，不返回 `embedding`。同文件增加 T002 的 `POST /internal/v1/memory`。`inferred` 且把握低于 0.8 只插入候选，不覆盖已有行。用户明确说记住时由助手稍后以 `explicit` 调用。客服、查账、工单不得调用写入
- [X] T014 在 `services/agent/app/api/invoke.py` 读取当次 `prompts` 和 `llm`。八个键缺一或正文为空时不进入图。测试和生产拒绝空 `llm`。本地 `llm` 为空时才读 `OPENAI_*`。同一轮只建一个模型客户端，密钥不写入日志。不在仓库里放会盖过数据库的提示词文件

**Checkpoint**: 错误令牌进不了图。开一轮能把链接、图片和恢复动作送进助手。路由纯函数已测过

---

## Phase 3: User Story 1 - 说「我要买纸巾」得到意图和真实商品 (Priority: P1) 🎯 MVP

**Goal**: 槽位够且把握不低于 0.6 时给出真实商品。不超过 6 条是商品卡，超过 6 条只有合集卡。关键词为空或把握低于 0.6 只追问

**Independent Test**: 发送「我要买纸巾」得到 `shop.search` 和来自购物服务的卡片。结果 6 条时商品卡为 6、合集卡为 0；7 条时相反。只有「买」时搜索次数为 0

### Tests for User Story 1

- [X] T015 [P] [US1] 先写会失败的 `services/agent/tests/test_shop.py`。假模型和 `httpx.MockTransport` 断言：把握低于 0.6 或关键词为空时搜索次数为 0；不超过 6 条只发商品卡；超过 6 条只发合集卡且封面最多 4 张；销量和评价数为空保持空白；搜不到时不编造商品；一句里同时有购买和查单时本轮只做主意图

### Implementation for User Story 1

- [X] T016 [US1] 在 `services/agent/app/graph/subgraphs/shop.py` 实现 `shop.search`。槽位够才调用 `search_goods`。不超过 6 条写入商品卡，超过 6 条只写入合集卡。未知销量、评价数保持空，不写成 0。失败时说明搜不到或暂时失败，回复不含联盟密钥或 PID
- [X] T017 [US1] 在 `services/agent/app/graph/builder.py` 接上 `ingest` → `memory_load` → `intent_router` → 追问或 `subgraph_shop` → `memory_write` → `reply_sse`。新的一句事件顺序是 `intent`，再追问或文本，再卡片，最后恰好一个终态
- [X] T018 [US1] 在 `services/agent/app/graph/nodes/memory_write.py` 识别用户明确说「记住」的预算或平台，调用 `POST /internal/v1/memory`，`source=explicit`，`kind` 为 `budget` 或 `platform`。`shop.search` 在这两项为空时读取未过期的 `explicit` 行填槽。这句话自己给了预算或平台就以这句话为准。`inferred` 候选不参与搜索。关键词为空仍然追问

**Checkpoint**: 假模型下「我要买纸巾」可单独验收。订单、停止和授权可以还没做

---

## Phase 4: User Story 2 - 查订单和账户时只复述服务里的数字 (Priority: P1)

**Goal**: 订单、花费、返佣、积分、授权状态和邀请人数都来自购物服务。没有物流就说未知。返佣只复述用户分成

**Independent Test**: 一笔无物流订单的答复含「未知」，用户分成出现 1 次，媒体佣金出现 0 次。问积分不产生支付单，助手不改余额

### Tests for User Story 2

- [X] T019 [P] [US2] 先写会失败的 `services/agent/tests/test_account.py`。断言物流为空时说未知且不编造运单号；没有该订单时说不知道；回复不出现 `mediaCommission` 和联盟原始报文；积分、钱包和邀请数字与工具返回一致；问「你了解我多少」只陈述 `get_me` 和记忆检索的结果

### Implementation for User Story 2

- [X] T020 [P] [US2] 在 `services/agent/app/graph/subgraphs/order.py` 只调用 `list_orders` / `get_order`。物流为空说未知。返佣只复述 `userCommission`
- [X] T021 [P] [US2] 在 `services/agent/app/graph/subgraphs/account.py` 实现 `me.profile`、`me.auth_list`、`me.stats`、`me.credits`。资料走 `get_me` 加记忆检索，统计走 `get_order_stats` 和 `get_wallet`，积分只读 `get_credits`。不创建支付单，不改余额
- [X] T022 [US2] 在 `services/agent/app/graph/subgraphs/earn.py` 实现 `earn.invite`，只调用 `get_affiliate_stats`。在 `services/agent/app/graph/builder.py` 把这三条子图接到 `reply_sse`，不发商品卡

**Checkpoint**: 查单和查账户不编造数字。搜品故事仍可单独通过

---

## Phase 5: User Story 3 - 停止会结束这一轮，刷新不会 (Priority: P1)

**Goal**: 点停止得到 `cancelled`，已写出的文字还在。只刷新则生成继续

**Independent Test**: 刷新 1 次且不点停止，终态不是 `cancelled`。另一轮点停止 1 次，终态为 `cancelled`，授权卡张数为 0

### Tests for User Story 3

- [X] T023 [P] [US3] 先写会失败的 JUnit，放在 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/chat/ChatRunTest.java`：取消标记会关闭内网流；只断开 SSE 阅读不写取消标记，工人继续转发
- [X] T024 [P] [US3] 先写会失败的 `services/agent/tests/test_cancel.py`：调用方断开后 `astream` 被取消，不写半截检查点，不使用 `interrupt()` 表示停止

### Implementation for User Story 3

- [X] T025 [US3] 在 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/chat/AgentGatewayImpl.java` 的读取循环里，每条事件前后查看 `chat:run:{runId}:cancel`。看到后关闭响应流。已转发的 `text_delta` 保留，再写 `cancelled`
- [X] T026 [US3] 在 `services/agent/app/api/invoke.py` 把连接断开转成任务取消，结束正在跑的图。不把停止实现成授权暂停，不把半截节点写入检查点

**Checkpoint**: 刷新和停止的终态不同。商品故事和查单故事不受影响

---

## Phase 6: User Story 4 - 只有授权和取消授权才等人确认 (Priority: P1)

**Goal**: 授权先给卡片并结束本轮。下一句从暂停点继续，不重新识别意图。未确认不取消授权

**Independent Test**: 未完成前授权状态不变，授权卡 1 张。带回确认后不再发第二张卡。取消工具只调用 1 次。说「我要买纸巾」不出现授权卡

### Tests for User Story 4

- [X] T027 [P] [US4] 先写会失败的 `services/agent/tests/test_hitl.py`。第一轮发出 `auth_card` 或 `confirm_card` 后结束。第二轮对同一 `thread_id` 提交恢复值，不产生新的 `intent`，不再发一张卡。`action=confirm` 时 `revoke_authorization` 只调用 1 次。未确认时不调用取消。购买、查单、闲聊和积分不足不发这两种卡

### Implementation for User Story 4

- [X] T028 [US4] 在 `services/agent/app/graph/nodes/hitl.py` 实现暂停。第一轮发出卡片后 `interrupt()` 并结束。下一句从暂停的下一行继续，调用 `start_authorization` 或 `revoke_authorization`，然后 `memory_write` → `reply_sse`。不进入 `intent_router`
- [X] T029 [US4] 确认 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/chat/ChatTurnServiceImpl.java` 在 `hitl` 非空且正文为空时仍受理，并把 `hitl` 放进 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/chat/AgentGatewayImpl.java` 的请求体。恢复请求使用同一个 `conversationId` 作为 `thread_id`

**Checkpoint**: 授权确认能完成。停止故事仍然不会弹出确认卡

---

## Phase 7: User Story 5 - 规则、工单、闲聊和其余购物说法走对路径 (Priority: P2)

**Goal**: 知识库未命中就说明并引导建单。工单返回工单号。链接、图片、比价、送礼和场景按各自槽位处理。闲聊不调业务工具

**Independent Test**: 未命中的规则不产生商品搜索。建单后能看到工单号。闲聊的商品搜索次数为 0。只发链接、不带文本时被受理

### Tests for User Story 5

- [X] T030 [P] [US5] 先写会失败的 `services/agent/tests/test_support.py`。知识库未命中时明确 miss 且不搜商品；命中时只复述命中内容；反馈返回当前用户的工单号；辱骂或无法判定建立加急工单；闲聊无业务工具。送礼和场景在关键词仍空时追问。链接打不开时说明打不开。图片认不出时追问。比价不发起大范围搜索。指代绑不上已出现的商品时追问，不猜编号

### Implementation for User Story 5

- [X] T031 [P] [US5] 在 `services/agent/app/graph/subgraphs/support.py` 实现 `kb.faq`、`ticket.create`、`handoff`。`kb.faq` 只调用 `search_kb`。建单调用 `create_ticket`。闲聊放在 `services/agent/app/graph/nodes/chitchat.py`，不调用业务工具
- [X] T032 [US5] 在 `services/agent/app/graph/subgraphs/shop.py` 补上 `shop.link`、`shop.image`、`shop.compare`、`shop.gift`、`shop.scene`。链接恰好一张卡。图片认不出则追问。比价只对已绑定商品调用 `goods_detail`。在 `services/agent/app/graph/nodes/coref.py` 只绑定本轮和检查点里上一轮的商品编号或订单编号，不读 `user_event`

**Checkpoint**: 规则、工单和闲聊不再掉进搜品。故事 1 的 6 条规则仍然有效

---

## Phase 8: User Story 6 - 这一轮只用运营配好的那套说法和那一个模型 (Priority: P2)

**Goal**: 八条提示词和本轮一个模型来自当次请求。生产留下追踪。用量按累计 token 扣费，最低 0.01 不按每一小段另算

**Independent Test**: 改一条提示词后，新的一轮使用新正文。同一轮模型名只有 1 个。累计 100 再到 150 token 时按 150 结算。生产关掉追踪时不进入图

### Tests for User Story 6

- [X] T033 [P] [US6] 先写会失败的 `services/agent/tests/test_model.py`。同一轮两次生成的模型名相同。仓库里没有会覆盖请求正文的提示词文件。`LANGFUSE_ENABLED` 在生产不为真时拒绝进入图，在本地为假时图仍结束。日志和追踪不含 `apiKey`
- [X] T034 [P] [US6] 先写会失败的 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/credit/CreditUsageTest.java`：累计 100 token 再到 150 token 只结算 150；同一累计值再到达一次，新增扣费为 0；两段各自低于 0.01 时不各扣一次 0.01，最低额作用在本轮累计结果上

### Implementation for User Story 6

- [X] T035 [US6] 在 `services/agent/app/graph/nodes/reply_sse.py` 于每次模型调用后发送累计 `usage`。字段 `promptTokens`、`completionTokens`、`totalTokens`、`credits` 都是本轮累计，`model` 等于本轮这一个模型名
- [X] T036 [US6] 在 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/credit/CreditBillingImpl.java` 于工人第一次转发 `usage` 时扣费。应扣金额是 `docs/conversation.md` 的公式作用于本轮累计 token 的结果，减去本轮已经扣过的金额。`done` 时多退少补，余额不为负。余额不够则掐断调用并写 `credit_exhausted`。助手不得到任何改积分的路径
- [X] T037 [US6] 在 `services/agent/app/observability/langfuse_setup.py` 用已锁定的 `langfuse` `2.57.0` 做追踪，`traceId` 沿用请求里的值。送出前去掉 `apiKey`。生产环境开关不为真时 `invoke.py` 直接拒绝。不引入第二套追踪产品

**Checkpoint**: 提示词修改能在下一轮看到。扣费与累计 token 一致

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: 按 quickstart 收口，并确认没有越界

- [X] T038 [P] 在 `services/agent/tests/test_pending.py` 增加一条多意图测试：本轮只执行主意图，另一件留在检查点的 `pending_intents`，不并行发两套结果
- [X] T039 按 `specs/005-agent/quickstart.md` 跑 `services/agent` 的 pytest 和 `mvn -pl shop-api-app -am test`。失败则修到通过。不把真模型响应录进测试
- [X] T040 [P] 抽查 `services/agent/app/` 与 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/chat/AgentGatewayImpl.java` 的日志、追踪和给用户的文字，模型密钥、联盟密钥、PID 的出现次数为 0。异常日志不出现调用正文
- [X] T041 确认本功能没有新增业务表、没有 Flyway、没有转链、没有改钱包、没有把助手端口暴露给消费者，也没有引入 Flutter 或第二个模型 SDK

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 可立即开始。T003 依赖 T001
- **Foundational (Phase 2)**: 依赖 Setup。未完成前不开始用户故事
- **User Stories (Phase 3–8)**: 都依赖 Foundational。建议按编号顺序做。故事 2 的工具封装可在故事 1 的图接上之后并行写
- **Polish (Phase 9)**: 依赖要交付的故事都完成

### User Story Dependencies

- **User Story 1 (P1)**: Foundational 之后即可开始，不依赖故事 2–6。这是 MVP
- **User Story 2 (P1)**: Foundational 之后可写工具和子图。独立验收不需要故事 1 的商品卡
- **User Story 3 (P1)**: 需要已经能往外推事件的图，建议在故事 1 之后
- **User Story 4 (P1)**: 依赖 Foundational 的检查点和 `hitl` 字段。不依赖搜品结果
- **User Story 5 (P2)**: 依赖故事 1 的导购子图和故事 2 的工具客户端
- **User Story 6 (P2)**: 依赖 Foundational 的模型注入。扣费测试不依赖页面

### Parallel Opportunities

- T002 可与 T001 并行
- T007 的路由测试可与 T009、T010 的 Java 改动并行
- T015、T019、T023、T024、T027、T030、T033、T034 都是先写失败测试，各自文件不同；同一故事里测试写完再实现
- T020 与 T021 可并行
- T038 与 T040 可并行

---

## Parallel Example: User Story 1

```bash
# 先只写失败测试：
services/agent/tests/test_shop.py

# 测试失败后，再实现搜品与回复：
services/agent/app/graph/subgraphs/shop.py
services/agent/app/graph/builder.py
services/agent/app/graph/nodes/memory_write.py
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1 和 Phase 2
2. 完成 Phase 3 故事 1
3. 停下来，用假模型验证「我要买纸巾」、6 条与 7 条、以及空关键词追问
4. 通过后再做故事 2

### Incremental Delivery

1. 故事 1：真实商品卡或合集卡
2. 故事 2：订单和账户数字不编造
3. 故事 3：停止与刷新分开
4. 故事 4：授权从暂停点恢复
5. 故事 5：规则、工单、闲聊和其余购物说法
6. 故事 6：一个模型、追踪和累计扣费
7. Phase 9 按 quickstart 收口

---

## Notes

- 每个任务都带文件路径。同一文件不要拆给两个并行任务
- 测试先失败再实现
- 不新增业务表，不把 checkpoint 表写进 Flyway
- 助手不写 `user_event`、订单、钱包和积分
- 停止用取消标记和掐断 HTTP。授权才用 `interrupt()`
