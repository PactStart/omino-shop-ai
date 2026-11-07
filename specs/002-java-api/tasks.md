---

description: "Task list for 002-java-api"
---

# Tasks: Java 双产出物的全部 HTTP、表和同步

**Input**: Design documents from `/specs/002-java-api/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: 规格要求故事 1–11 各有测试或 curl。每个故事先写会失败的测试，再实现。

**Organization**: 按用户故事 1 到 11 的编号施工。优先级标签不改变这个顺序。前一个故事的独立测试未通过，不开始下一个。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件，且不依赖未完成任务）
- **[Story]**: 用户故事标签，仅用户故事阶段使用

## Path Conventions

- Java：`services/java/shop-api-common`、`shop-api-app`、`shop-api-admin`
- 包名：`com.omino.shopai`（common）、`com.omino.shopai.app`、`com.omino.shopai.admin`
- 权威契约：`docs/data-model.md`、`docs/app-api-catalog.md`、`docs/api-admin.md`、`docs/api-internal.md`、`docs/conversation.md`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 把新依赖和本功能新契约先写进权威文档，再动 POM

- [X] T001 在 `AGENTS.md` §2.1 登记 `com.baomidou:mybatis-plus-generator` `3.5.9`（只生成 Mapper 与 XML，不进运行时 jar）、`com.github.wechatpay-apiv3:wechatpay-java` `0.2.17`（只用下单与回调验签）、`com.alipay.sdk:alipay-sdk-java` `4.40.1044.ALL`（只用回调验签与单笔转账）。写明许可证 Apache-2.0，以及不用的部分
- [X] T002 把 T001 的三个坐标写入 `services/java/pom.xml` 的 `dependencyManagement`。生成器只出现在 `services/java/shop-api-common/pom.xml` 的 test 范围。`wechatpay-java` 只出现在 `services/java/shop-api-app/pom.xml`。`alipay-sdk-java` 同时出现在 `shop-api-app`（回调验签）和 `shop-api-admin`（单笔转账）。执行依赖树之后，版本锁定才算完成。确认没有抬高 Boot `3.4.11`、MyBatis-Plus `3.5.9` 或其它已锁定版本；若支付宝坐标抬高版本，改用仍为 Apache-2.0 且不抬版本的最新 `4.40.x`，并改回 `AGENTS.md` 与 `specs/002-java-api/research.md`。微信库不得进入 Admin
- [X] T003 把 `specs/002-java-api/data-model.md` 的提现规则补进 `docs/data-model.md`：`withdrawal.status` 只允许 `pending`、`paid`、`rejected`；`channel` 本功能只允许 `alipay`；新增可空列 `provider_transfer_id VARCHAR(64)` 与 `paid_at TIMESTAMPTZ(3)`。`provider_transfer_id` 只在打款成功后保存通道单号，失败不写；发给通道的幂等号永远是 `withdrawal.id`，不从该列读取。冻结不写 `wallet_ledger`，打款成功才写 `type=withdraw`。打款或拒绝前先锁钱包行，再在同一事务里重读状态，只有仍为 `pending` 才继续
- [X] T004 把 `specs/002-java-api/contracts/admin-session.md` 的三条路径写入 `docs/api-admin.md`：`GET /api/admin/v1/auth/login`、`GET /api/admin/v1/auth/callback`、`POST /api/admin/v1/auth/logout`。cookie 名 `sid_admin`。未登录业务接口为 HTTP `401` 且信封 `code` 为 `40100`
- [X] T005 把 `specs/002-java-api/contracts/withdrawal.md` 写入 `docs/app-api-catalog.md` 与 `docs/api-admin.md`：消费者钱包与提现四条路径，运营列表、`/payout`、`/reject`。打款失败时 HTTP 200、`code` 为 `0`、`message` 为「打款未成功，冻结额未动」、`data.payoutResult=failed`，不新增错误码

**Checkpoint**: 权威文档已包含新依赖、提现列和两类新路径。代码还没有这些类

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 所有故事共用的信封、错误码、持久化扫描和内部令牌。完成前不写业务接口

**⚠️ CRITICAL**: 用户故事开始前必须完成本阶段

- [X] T006 在 `services/java/shop-api-common/src/main/java/com/omino/shopai/api/ErrorCodes.java` 列出本功能会用到的码：`0`、`40001`、`40100`、`40300`、`40301`、`40303`、`40400`、`40900`、`50010`、`50020`。不新增提现失败码
- [X] T007 在 `services/java/shop-api-common/src/main/java/com/omino/shopai/api/ApiExceptionHandler.java` 把校验失败映射为 `40001`，未登录映射为 HTTP `401` 与 `code` `40100`，未知异常映射为 `50000` 且 `message` 不含堆栈、SQL、密钥
- [X] T008 在 `services/java/shop-api-common/src/main/java/com/omino/shopai/config/MybatisPlusConfig.java` 扫描 `com.omino.shopai.mapper`，XML 目录为 `classpath*:/mapper/**/*.xml`。两个应用模块都引入该配置，且 `shop-api-app` 与 `shop-api-admin` 的 POM 仍然互不依赖
- [X] T009 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/security/InternalTokenFilter.java`，并用 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/security/InternalTokenFilterTest.java` 先写失败测试：`/internal/v1/**` 缺少或错误的 `X-Internal-Token` 返回 HTTP `401`、`code` `40100`，且要求 `X-User-Id`。这道过滤器在故事 8 之前就必须生效

**Checkpoint**: 业务异常有统一信封。Mapper 扫描就位。内部工具没有令牌进不去。还没有业务表

---

## Phase 3: User Story 1 - 能登录并记下邀请人 (Priority: P1) 🎯 MVP

**Goal**: 空库有全部业务表；手机号验证码登录签发 APP JWT；有效邀请码同一事务写入 `app_user.inviter_id` 且关系为有效；常规 Mapper XML 由生成器产出

**Independent Test**: 本地 `SMS_PROVIDER=mock` 时验证码为 `888888`，登录得到凭证。第二个用户带第一个用户的邀请码登录后上级已写入。自己邀请自己被拒绝。无凭证访问需登录接口为 `401` / `40100`。一张表有生成的 XML，且其中没有联盟判断

### Tests for User Story 1

- [X] T010 [P] [US1] 先写失败测试 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/auth/SmsLoginTest.java`：mock 短信验证码 `888888` 登录成功；错误验证码不签发凭证；无凭证调用需登录接口得到 HTTP `401` 与 `code` `40100`
- [X] T011 [P] [US1] 先写失败测试 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/auth/InviteBindTest.java`：有效且非本人的邀请码在同一事务写入 `app_user.inviter_id` 与 `app_invite.status=valid`；自己邀请自己被拒绝；已有上级不被覆盖
- [X] T012 [P] [US1] 先写失败测试 `services/java/shop-api-common/src/test/java/com/omino/shopai/mapper/MapperXmlGeneratorTest.java`：已提交的 `app_user` XML 在 `src/main/resources/mapper/`，内容不含联盟分支，且仓库里没有由生成器产出的 Entity 或 Service。本测试不连接数据库，也不重跑生成器

### Implementation for User Story 1

- [X] T013 [US1] 新增 `services/java/shop-api-common/src/main/resources/db/migration/V2__business_tables.sql`，按 `docs/data-model.md` 建全部业务表，含 T003 的提现列。不建 LangGraph checkpoint 表。保留 `V1__enable_pgvector.sql`。只提交向前迁移，不写 down。回退靠恢复备份
- [X] T014 [P] [US1] 手写 `services/java/shop-api-common/src/main/java/com/omino/shopai/entity/AppUser.java` 与 `AppInvite.java`，字段与权威表一致，邀请码创建后不可改，`inviter_id` 可空且不得等于自身
- [X] T015 [US1] 在 `services/java/shop-api-common/src/test/java/com/omino/shopai/mapper/MapperXmlGenerator.java` 调用 `mybatis-plus-generator` `3.5.9`，只输出 Mapper 接口到 `com.omino.shopai.mapper` 和 XML 到 `resources/mapper`。手写 SQL 放 `*.custom.xml`，生成器不得覆盖它们。改表时在本地已迁移库上跑一次并提交产物。CI 不重跑生成器。跑完后 T012 通过
- [X] T016 [US1] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/auth/SmsAuthService.java`：local 的 mock 固定码 `888888`；生产禁止 mock；登录签发 APP JWT
- [X] T017 [US1] 在 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/auth/SmsAuthService.java` 的同一事务里绑定邀请：写入 `inviter_id` 与 `app_invite.status=valid`、`valid_at` 为当前时刻。拒绝自己邀请自己，已有上级不覆盖
- [X] T018 [US1] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/web/app/AuthController.java` 的 `POST /api/app/v1/auth/sms/send` 与 `POST /api/app/v1/auth/sms/login`，以及 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/security/AppJwtFilter.java`：缺凭证返回 `401` / `40100`

**Checkpoint**: 不启动联盟、支付或 SSO，也能登录并验证邀请绑定

---

## Phase 4: User Story 2 - 开一轮对话并随时接上或停止 (Priority: P1)

**Goal**: 提交对话立刻返回 `runId`；事件进 Redis；订阅间隔不超过 100 毫秒；断开不取消；停止写入 `cancelled`。`test` 与 `prod` 调用 Agent，失败写 `error` / `50020`。终态后 Stream 再保留 30 分钟

**Independent Test**: 登录后开一轮得到 `runId`。断开订阅后再读，已有事件仍在。取消后终态为 `cancelled`，已写入的 `text_delta` 还在。不要求真实商品。Agent 不可用时终态是 `error` 且 `code` 为 `50020`，不是样例 `done`

### Tests for User Story 2

- [X] T019 [P] [US2] 先写失败测试 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/chat/ChatRunTest.java`：`POST /api/app/v1/chat/turns` 立刻返回 `runId`；事件名属于对话契约；断开后再次读取仍能看到已写入事件；`POST /api/app/v1/chat/runs/{runId}/cancel` 写入 `cancelled`；对已终态的轮再次取消仍成功。测试里的 Agent 网关替身返回失败时，终态事件为 `error` 且 `code` 为 `50020`，不得出现样例 `done`

### Implementation for User Story 2

- [X] T020 [P] [US2] 手写 `services/java/shop-api-common/src/main/java/com/omino/shopai/entity/Conversation.java` 与 `ConversationMessage.java`，并为它们生成 Mapper XML（沿用 T015 的生成器，不覆盖 `*.custom.xml`）
- [X] T021 [US2] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/chat/ChatTurnService.java`：受理后立刻分配 `runId` 并写入用户消息，不等待生成结束
- [X] T022 [US2] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/chat/ChatEventPump.java`：从 Redis Stream `chat:run:{runId}` 读取，间隔不超过 100 毫秒。订阅断开不得写取消键。`done`、`error`、`cancelled`、`credit_exhausted` 之后该键再保留 30 分钟
- [X] T023 [US2] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/chat/ChatCancelService.java`：只在取消请求时 `SET chat:run:{runId}:cancel`，再写入事件 `cancelled`。已终态的轮幂等成功
- [X] T024 [US2] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/chat/ChatRunWorker.java`：`test` 与 `prod` 调用 Agent 网关，按锁定事件名写入流。Agent 不可用时写 `error` 且 `code` 为 `50020`，不写样例 `done`。看到取消键后停止并保留已写入的 `text_delta`。未结束的轮按 `docs/conversation.md` 超时写成 `error`。`local` 与测试双写可改用同目录的 `FixtureChatWorker.java`，`test` 与 `prod` 不得注册它
- [X] T025 [US2] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/web/app/ChatController.java`，挂上 turns、events 与 cancel 三条路径

**Checkpoint**: 测试双写即可证明开轮、续传、停止和 Agent 失败。不要求启动真实 Python 进程。`test` 与 `prod` 的代码路径不得注册样例 Worker

---

## Phase 5: User Story 3 - 看详情、过转链门闩、留下浏览收藏和合集 (Priority: P1)

**Goal**: 详情与瘦快照在销量或评价数未知时为 null。未授权转链返回 `40301` 且 `error.reason=NEED_AUTH`，不下发推广位标识。浏览、收藏、合集可读写

**Independent Test**: 未授权转链得到 `40301` / `NEED_AUTH`。收藏同一商品两次只有一条。移除别人的合集商品得到 `40300`

### Tests for User Story 3

- [X] T026 [P] [US3] 先写失败测试 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/goods/GoodsGateTest.java`：未授权转链为 `40301` 且 `error.reason` 为 `NEED_AUTH`；响应与日志不含推广位标识；平台未给出的 `salesCount` 与 `reviewCount` 为 null 而不是 0
- [X] T027 [P] [US3] 先写失败测试 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/goods/GoodsLibraryTest.java`：收藏幂等；非本人移除合集商品为 `40300`；移除后的商品不再出现在本人合集

### Implementation for User Story 3

- [X] T028 [P] [US3] 手写 `GoodsSnapshot.java`、`GoodsFavorite.java`、`GoodsCollection.java`、`GoodsCollectionItem.java`、`ClickEvent.java`、`PlatformAuthorization.java` 到 `services/java/shop-api-common/src/main/java/com/omino/shopai/entity/`，并生成对应 Mapper XML
- [X] T029 [US3] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/goods/GoodsQueryService.java`：详情与快照的未知销量、评价数为 null
- [X] T030 [US3] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/goods/GoodsConvertLinkService.java`：未授权返回 `40301` / `NEED_AUTH`；成功才写 `click_event`。禁止把 PID 或 relationId 放进响应
- [X] T031 [US3] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/goods/GoodsLibraryService.java`：浏览来自 `user_event.kind=browse`，收藏幂等，合集移除写 `removed_at`，非本人为 `40300`
- [X] T032 [US3] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/web/app/GoodsController.java` 的详情、转链、浏览、收藏、合集路径，对齐 `docs/app-api-catalog.md`

**Checkpoint**: 不连接真实联盟，转链门闩和收藏合集已可测

---

## Phase 6: User Story 4 - 四个电商平台可查询 (Priority: P2)

**Goal**: 淘宝、京东、拼多多、唯品会的访问只发生在平台适配层。失败为 `50010`。没有美团

**Independent Test**: 四个客户端都存在。查询失败得到 `50010`。美团平台码被拒绝

### Tests for User Story 4

- [X] T033 [US4] 先写失败测试 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/adapter/PlatformClientTest.java`：四个平台失败映射为 `50010`；`meituan` 被拒绝；测试代码引用的客户端类都在 `com.omino.shopai.app.adapter.platform`

### Implementation for User Story 4

- [X] T034 [P] [US4] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/adapter/platform/PlatformGoodsClient.java` 与四个实现类 `TaobaoGoodsClient.java`、`JdGoodsClient.java`、`PddGoodsClient.java`、`VipGoodsClient.java`。密钥只读环境变量
- [X] T035 [US4] 改 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/goods/GoodsQueryService.java`，只通过 `PlatformGoodsClient` 搜品和取详情。上游失败转为 `50010`。禁止在 service 或 XML 里 new 联盟 SDK

**Checkpoint**: 无联盟密钥时，失败路径为 `50010`，而不是未捕获异常

---

## Phase 7: User Story 5 - 完成的订单只入账一次，并能提现 (Priority: P2)

**Goal**: 订单六种状态、结算三种状态。已完成且待结算只给下单用户入账一次。提现申请冻结，打款成功才扣冻结额并记流水，失败保持冻结，拒绝则解冻

**Independent Test**: 同一完成单同步两次仍只有一条返佣流水。申请提现后可用余额减少、冻结额增加。两笔并发申请后余额不为负。打款失败冻结额不变，响应说明未打出，记录仍是 `pending`。打款成功才有一条 `withdraw` 流水。拒绝会解冻。消费者订单响应没有媒体佣金

### Tests for User Story 5

- [X] T036 [P] [US5] 先写失败测试 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/order/OrderSettleOnceTest.java`：`order_status=completed` 且 `settle_status=pending` 且 `user_commission>0` 时只写一条 `wallet_ledger.type=commission`；再同步一次不增加余额；不给上级写第二笔；消费者响应不含 `mediaCommission`
- [X] T037 [P] [US5] 先写失败测试 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/wallet/WithdrawalTest.java` 与 `services/java/shop-api-common/src/test/java/com/omino/shopai/support/withdrawal/WithdrawalTransitionTest.java`：申请把金额从 `available_amount` 划入 `frozen_amount` 且不写流水；两个事务交错申请时可用余额与冻结额都不为负；打款失败保持 `pending` 与冻结额，不写 `provider_transfer_id`，`message` 为「打款未成功，冻结额未动」，`payoutResult=failed`；再次打款成功才变为 `paid`，写入通道单号，并只有一条 `type=withdraw`；拒绝变为 `rejected` 并解冻；`channel` 不是 `alipay` 时为 `40001`

### Implementation for User Story 5

- [X] T038 [P] [US5] 手写 `AppOrder.java`、`OrderSyncCursor.java`、`Wallet.java`、`WalletLedger.java`、`Withdrawal.java` 到 `services/java/shop-api-common/src/main/java/com/omino/shopai/entity/`。`Withdrawal.status` 只允许 `pending`、`paid`、`rejected`。`provider_transfer_id` 为可空 `VARCHAR(64)`，只在成功后保存通道单号。`paid_at` 为可空 `TIMESTAMPTZ`。生成 Mapper XML
- [X] T039 [US5] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/order/OrderSyncService.java` 与 `job/OrderSyncJob.java`：按平台游标写入统一订单。本批订单写入与该平台游标推进在同一事务，失败则游标停在原处。`order_status` 只使用 `pending_pay`、`pending_ship`、`shipped`、`completed`、`cancelled`、`unknown`。`settle_status` 只使用 `none`、`pending`、`settled`。分成读取 `commission.user-share-rate`，默认 `0.80`
- [X] T040 [US5] 在 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/order/OrderSyncService.java` 的同一事务里入账：先 `SELECT ... FOR UPDATE` 锁定该用户钱包行，再仅给下单用户写一条 `commission` 流水、回填 `wallet_ledger_id`。已有流水则跳过。已结算后不改历史流水。可用余额不得为负
- [X] T041 [US5] 实现 `services/java/shop-api-common/src/main/java/com/omino/shopai/support/withdrawal/WithdrawalTransition.java`（纯函数，不打开事务、不依赖 Servlet）和同包的 `PayoutPort.java`。支付宝实现放在 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/adapter/pay/AlipayPayoutClient.java`。发给通道的幂等号永远是 `withdrawal.id`，不从 `provider_transfer_id` 读取。测试双写实现同一个 `PayoutPort`，可以返回成功或失败
- [X] T042 [US5] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/wallet/WithdrawalService.java`：只处理申请。先锁定钱包行，金额划入冻结额，不写流水，余额不得为负。打款与拒绝放在 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/service/wallet/AdminWithdrawalService.java`，调用 `WithdrawalTransition` 与 `PayoutPort`。锁住钱包行之后，在同一事务里重读该笔提现，只有仍为 `pending` 才打款或拒绝。失败保持冻结且不写通道单号。成功才扣冻结额并记一条 `withdraw`。已 `paid` 不再打款。Admin 不得依赖 APP 模块
- [X] T043 [US5] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/web/app/WalletController.java` 与 `OrderController.java`，路径对齐 `docs/app-api-catalog.md` 与 T005。消费者订单不下发媒体佣金、上级标识和流水标识

**Checkpoint**: 用样本订单和测试打款端口即可证明入账一次和提现状态机。不要求真实出款

---

## Phase 8: User Story 6 - 用钱买积分，积分不能变回钱 (Priority: P2)

**Goal**: 三个渠道都能下单。钱包当场扣余额并加积分。微信和支付宝验签通过才入账，同一回调只入账一次。积分不能提现或转回钱包

**Independent Test**: 钱包购积分两边各有流水。测试密钥的非法回调不加积分，合法回调只加一次。不存在积分转回钱包的路径

### Tests for User Story 6

- [X] T044 [P] [US6] 先写失败测试 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/credit/CreditPurchaseTest.java`：`channel=wallet` 当时扣 `wallet.available_amount` 并增加积分，且先锁定钱包行；与一笔提现交错时余额不为负；`wechat_pay` 与 `alipay` 验签失败不加积分；同一合法回调第二次不增加积分。不连接真实商户
- [X] T045 [P] [US6] 先写失败测试 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/credit/CreditBoundaryTest.java`：可用积分低于 `0.01` 且强制扣费时，开轮仍返回 `runId`，事件流以 `credit_exhausted` 结束并含 `credit_card`；没有把积分转回钱包的路由

### Implementation for User Story 6

- [X] T046 [P] [US6] 手写 `CreditAccount.java`、`CreditLedger.java`、`CreditPackage.java`、`CreditOrder.java`、`LlmUsage.java` 到 `services/java/shop-api-common/src/main/java/com/omino/shopai/entity/` 并生成 Mapper XML。积分 `available` 不得为负
- [X] T047 [US6] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/adapter/pay/WechatPayNotifyVerifier.java` 与 `AlipayNotifyVerifier.java`，分别使用 `wechatpay-java` `0.2.17` 与 `alipay-sdk-java`。验签失败不入账
- [X] T048 [US6] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/credit/CreditPurchaseService.java`：三渠道下单；`wallet` 在已锁定钱包行的同一事务里当场扣款；回调成功写 `credit_ledger.type=purchase` 且同一回调只写一次
- [X] T049 [US6] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/credit/CreditBillingService.java`：开轮前余额不足时不调 Agent，写入 `credit_card` 与 `credit_exhausted`。未调用模型不扣费
- [X] T050 [US6] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/web/app/CreditController.java`，含套餐、下单、查询和两条无 JWT 的支付回调。不注册积分提现或转回钱包的路径

**Checkpoint**: 测试密钥下，三个渠道的入账次数正确

---

## Phase 9: User Story 7 - 拿到海报模板和邀请关系 (Priority: P2)

**Goal**: 下发邀请码、邀请链接和生效中的模板矩形。不合成成品图。公开查询不返回邀请人身份

**Independent Test**: 海报响应只有原图与矩形。存在的邀请码只表示有效，不存在为 `40400`，没有昵称和手机号

### Tests for User Story 7

- [X] T051 [US7] 先写失败测试 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/invite/InvitePosterTest.java`：生效模板含矩形且无成品图字段；过期或下架模板不出现；`GET /api/app/v1/invite/lookup` 不返回昵称或手机号

### Implementation for User Story 7

- [X] T052 [US7] 手写 `InvitePosterTemplate.java` 到 `services/java/shop-api-common/src/main/java/com/omino/shopai/entity/` 并生成 Mapper XML
- [X] T053 [US7] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/invite/InvitePosterService.java`：只查询 `status=active` 且当前时间在生效范围内的模板，按 `sort` 升序
- [X] T054 [US7] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/web/app/InviteController.java` 的 `/invite`、`/invite/posters`、`/invite/lookup`。不注册合成成品图的路径

**Checkpoint**: 响应里没有服务端合成的海报

---

## Phase 10: User Story 8 - 查知识库并留下带日志的工单 (Priority: P3)

**Goal**: 知识库未命中必须明确未命中。建单同一事务写 `ticket_log.kind=created`。后续变更只追加日志

**Independent Test**: 检索不到规则时结果为未命中。建单后有一条创建日志。改一次状态再增加一条，旧日志不变

### Tests for User Story 8

- [X] T055 [US8] 先写失败测试 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/support/TicketLogTest.java`：未命中不是一条编造的政策；建单写 `kind=created`；状态变更追加 `kind=status_change`；历史行没有 UPDATE

### Implementation for User Story 8

- [X] T056 [P] [US8] 手写 `KbArticle.java`、`Ticket.java`、`TicketLog.java` 到 `services/java/shop-api-common/src/main/java/com/omino/shopai/entity/` 并生成 Mapper XML。`ticket_log` 不提供更新或删除方法
- [X] T057 [US8] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/kb/KbSearchService.java` 与 `service/ticket/TicketService.java`：未命中显式返回；建单、改状态、改优先级、证据、回复、改派都在同一事务追加对应 `kind`
- [X] T058 [US8] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/web/internal/InternalKbController.java` 与 `InternalTicketController.java`，路径对齐 `docs/api-internal.md`。这些控制器走基础阶段的内部令牌过滤器，不得再调用 Agent

**Checkpoint**: 工单时间线只增不改。不需要运营页面

---

## Phase 11: User Story 9 - 按端和渠道拿到版本、配置、公告和私信 (Priority: P3)

**Goal**: 客户端头缺失为 `40001`。检查更新、特殊配置浅合并、公告过滤、点对点站内信可用

**Independent Test**: 缺 `X-Client-Type`、`X-App-Channel` 或 `X-App-Version-Code` 得到 `40001`。一封私信只有目标用户能读到

### Tests for User Story 9

- [X] T059 [US9] 先写失败测试 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/meta/AppMetaTest.java`：缺客户端头为 `40001`；登录、健康检查和支付回调不要求这些头；点对点站内信不出现在其他用户的收件箱；同端同渠道同一天不能插入第二条相同 `version_code`

### Implementation for User Story 9

- [X] T060 [P] [US9] 手写 `AppRelease.java`、`AppRuntimeConfig.java`、`Announcement.java`、`InboxMessage.java` 到 `services/java/shop-api-common/src/main/java/com/omino/shopai/entity/` 并生成 Mapper XML。`version_code` 为 `yyyyMMdd` 八位整数
- [X] T061 [US9] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/web/app/ClientHeaderFilter.java`：业务路径缺头返回 `40001`；放行登录、健康检查和支付回调
- [X] T062 [US9] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/app/AppReleaseService.java` 与 `AppRuntimeConfigService.java`：检查更新按端和渠道取最大已发布版本；多条配置按 `priority` 降序浅合并
- [X] T063 [US9] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/inbox/InboxService.java` 与 `web/app/InboxController.java`、`AppMetaController.java`：公告按端、渠道、版本和时间过滤；私信只属于一个 `userId`

**Checkpoint**: 不需要管理端页面，消费者侧的版本、配置、公告和私信已可测

---

## Phase 12: User Story 10 - 配置提示词和本轮用哪个模型 (Priority: P3)

**Goal**: 八个提示词键只能改正文。模型密钥加密保存，读取不回明文。开轮注入全部提示词和抽中的一条模型。推荐问题同时上架最多 12 条

**Independent Test**: 未知提示词键为 `40400`。模型列表没有密钥明文。第 13 条上架为 `40001`。开一轮的注入体含八个键和唯一模型

### Tests for User Story 10

- [X] T064 [P] [US10] 先写失败测试 `services/java/shop-api-admin/src/test/java/com/omino/shopai/admin/agent/PromptAndModelTest.java`：八个键以外为 `40400`；空正文被拒绝；读取模型只有 `apiKeySet` 与 `apiKeyMask`，没有明文；省略密钥的更新保留原密钥
- [X] T065 [P] [US10] 先写失败测试 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/chat/RunInjectionTest.java`：开轮注入八条非空提示词和一条模型；缺任一键时不调用 Agent 并以 `50020` 结束；本地且无候选模型时允许不注入；上架推荐问题超过 12 条为 `40001`

### Implementation for User Story 10

- [X] T066 [P] [US10] 手写 `ChatSuggestion.java`、`AgentPrompt.java`、`LlmModel.java` 到 `services/java/shop-api-common/src/main/java/com/omino/shopai/entity/` 并生成 Mapper XML。`api_key_enc` 只存 AES-GCM 密文，密钥材料为 `LLM_CONFIG_KEY`
- [X] T067 [US10] 实现 `services/java/shop-api-common/src/main/java/com/omino/shopai/support/LlmKeyCipher.java`：加密与末四位掩码。日志不得接收明文参数
- [X] T068 [US10] 实现 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/agent/RunContextLoader.java`：读全表八个键；按 `enabled=1` 且 `weight>0` 抽一条模型，同一轮只抽一次。把结果交给 `ChatRunWorker` 再调用 Agent 网关。`test` 与 `prod` 没有候选模型时不调用 Agent，并以 `50020` 结束。本故事仍不实现对话图
- [X] T069 [US10] 在后续管理接口会调用的 `services/java/shop-api-common` 之外，先把读写放进 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/agent/AgentConfigService.java`：提示词只能改正文；推荐问题同时 `active` 最多 12 条。Admin 控制器留到 US11，避免 app 与 admin 互相依赖

**Checkpoint**: 加密、八键和分流可测。管理端 HTTP 在 US11 暴露

---

## Phase 13: User Story 11 - 运营登录后才能改配置 (Priority: P3)

**Goal**: 管理端用 omino-sso 的 Authorization Code + PKCE S256。浏览器不持有令牌。未登录进不了业务接口。上帝视角生产默认关闭

**Independent Test**: 不带 `sid_admin` 调用运营业务接口得到 `401` / `40100`，响应无用户资料和密钥。`GOD_VIEW_ENABLED` 未开启时签发票据为 `40303`。没有管理员密码表

### Tests for User Story 11

- [X] T070 [P] [US11] 先写失败测试 `services/java/shop-api-admin/src/test/java/com/omino/shopai/admin/security/AdminSessionTest.java`：未登录访问 `/api/admin/v1/users` 为 HTTP `401`、`code` `40100`；健康检查仍不要求会话；响应不含 access_token
- [X] T071 [P] [US11] 先写失败测试 `services/java/shop-api-admin/src/test/java/com/omino/shopai/admin/godview/GodViewTest.java`：未开启时 `POST /api/admin/v1/god-view/tokens` 为 `40303`；开启后票据带 `gv=1`、有效期不超过 1 小时，且写 `admin_audit_log`

### Implementation for User Story 11

- [X] T072 [US11] 实现 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/security/AdminOAuth2Config.java`：使用已锁定的 Spring Security OAuth2 Client，PKCE S256，回调路径 `/api/admin/v1/auth/callback`。会话 cookie 名 `sid_admin`。不得依赖 Boot `3.5.3`，不得建密码表
- [X] T073 [US11] 实现 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/web/AdminAuthController.java` 的 login、callback、logout。登出清本地会话并引导到 IdP `/logout`。令牌只留在服务端会话
- [X] T074 [P] [US11] 实现 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/web/AdminUserController.java` 与 `AdminOrderController.java`：用户档案与订单查询对齐 `docs/api-admin.md`。订单可含媒体佣金；响应无 PID、无完整收款账号
- [X] T075 [P] [US11] 实现 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/web/AdminWithdrawalController.java`：列表、`/payout`、`/reject` 只调用故事 5 已放在 Admin 的 `AdminWithdrawalService`。不要把打款客户端搬回 APP，也不要在这一步再搬状态机。失败响应 `code` 为 `0`，`message` 为「打款未成功，冻结额未动」，`payoutResult` 为 `failed`，记录仍能按 `pending` 查到
- [X] T076 [P] [US11] 实现 `AdminCreditController.java`、`AdminInvitePosterController.java`、`AdminReleaseController.java`、`AdminRuntimeConfigController.java`、`AdminAnnouncementController.java`、`AdminInboxController.java`，放在 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/web/`。站内信必须带单个 `userId`，不得当群发
- [X] T077 [US11] 实现 `AdminChatSuggestionController.java`、`AdminAgentPromptController.java`、`AdminLlmModelController.java` 与 `AdminGodViewController.java`。提示词无 POST/DELETE。模型响应只有 `apiKeySet` 与 `apiKeyMask`。上帝视角未开启返回 `40303`

**Checkpoint**: 未登录打不到运营业务接口。页面仍然不做

---

## Phase 14: Polish & Cross-Cutting Concerns

**Purpose**: 把内部工具白名单和验收清单收口

- [X] T078 按 `docs/api-internal.md` 的白名单核对 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/web/internal/`。补上尚未存在的 `search_goods`、`get_me`、`list_orders`、`get_wallet`、`get_credits`。内部令牌过滤器已在基础阶段落地，这里只核对路径闭集。内部控制器不得调用 Agent，不得提供转链或改积分
- [X] T079 在 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/security/SecretLeakTest.java` 断言抽样响应不含模型密钥明文、联盟 PID 和完整收款账号
- [X] T080 按 `specs/002-java-api/quickstart.md` 跑 `mvn -pl shop-api-app,shop-api-admin -am test`，并记下故事 1–11 哪一条对应该文件里的检查

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无前置，先改权威文档和版本表
- **Foundational (Phase 2)**: 依赖 Setup。未完成不得开始用户故事
- **User Story 1–11**: 严格按编号。US2 依赖 US1 的登录；其后每个故事依赖前一个故事的独立测试通过
- **Polish**: 依赖 US11

### User Story Dependencies

- **US1**: 基础完成即可开始。不依赖其它故事
- **US2**: 依赖 US1 的 JWT
- **US3**: 依赖 US1 的用户。转链门闩不依赖 US4 的真实客户端，测试可用失败客户端
- **US4**: 依赖 US3 的查询服务入口
- **US5**: 依赖 US4 的平台客户端接口；入账测试可用样本订单，不依赖真实联盟
- **US6**: 依赖 US1 的用户与 US2 的开轮。支付不依赖 US5 的提现
- **US7**: 依赖 US1 的邀请码
- **US8**: 依赖 US1 的用户
- **US9**: 依赖 US1 的用户
- **US10**: 依赖 US2 的开轮
- **US11**: 依赖前面故事已经放在 common 的实体，以及故事 5 放在 Admin 的提现打款服务。运营 HTTP 在本故事接上会话。故事 5 不暴露未登录的打款接口

故事之间不并行。同一故事里标了 [P] 的测试或实体可以并行。

### Parallel Opportunities

- T010、T011、T012 可同时写
- T026 与 T027、T036 与 T037、T044 与 T045、T064 与 T065、T070 与 T071 可同时写
- 同一故事中不同实体文件可同时写
- 不同用户故事不可同时开工

### Parallel Example: User Story 1

```bash
# 三个失败测试可同时写：
services/java/shop-api-app/src/test/java/com/omino/shopai/app/auth/SmsLoginTest.java
services/java/shop-api-app/src/test/java/com/omino/shopai/app/auth/InviteBindTest.java
services/java/shop-api-common/src/test/java/com/omino/shopai/mapper/MapperXmlGeneratorTest.java
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1 与 Phase 2
2. 完成 Phase 3 的失败测试，再实现到 T018
3. 停下来跑 US1 的三个测试
4. 通过后再进入 US2

故事 1 是施工上的第一刀。对购物用户可演示的停点是故事 3。资金规则的停点是故事 5。

### Incremental Delivery

按 Phase 3 到 Phase 13 逐个故事交付。每个 Checkpoint 都是一次可停止的验收。不要跳到后面的故事去补前面的表或接口。

### Parallel Team Strategy

本功能不拆给多人同时做不同故事。同一故事里的失败测试和互不依赖的实体可以分头写。

---

## Notes

- 先写测试并确认失败，再写实现
- 生成器不产出 Entity、Service、Controller，也不覆盖 `*.custom.xml`。改表时本地跑一次并提交 XML，CI 不重跑
- 不复制 mall-guide。不建管理员密码表。不做页面，不实现 LangGraph
- `test` 与 `prod` 的对话 Worker 调用 Agent。样例 Worker 只在 `local` 和测试双写
- 微信验签只在 APP。支付宝转账只在 Admin。钱包改动先锁行
- 提交按故事或一组任务进行，不要把 11 个故事放进一次提交
