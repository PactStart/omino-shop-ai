---

description: "Task list for 004-admin-web"
---

# Tasks: 管理后台页面

**Input**: Design documents from `/specs/004-admin-web/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/admin-additions.md, quickstart.md

**Tests**: 计划点名的四组 JUnit 要先写失败测试再实现：推荐问题读回与第 13 条上架、工单回复不关单、知识库下架后检索不命中、同步重试拒绝第五个平台且审计不含密钥。页面不另加前端测试框架，浏览器验收在收尾按 quickstart 走。

**Organization**: 按用户故事 1 到 6 施工。故事 2 先做只读，故事 6 再补这些页上的写操作。故事 4 里依赖新列表的用户详情栏，等故事 2 和故事 6 的接口完成后再接。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件，且不依赖未完成任务）
- **[Story]**: 用户故事标签，仅用户故事阶段使用

## Path Conventions

- 管理前端：`apps/admin-web/`
- Admin 接口：`services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/`
- 分页类型：`services/java/shop-api-common/src/main/java/com/omino/shopai/dto/PageDto.java`
- 同步执行：`services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/order/OrderSyncService.java`
- 权威文档：`docs/api-admin.md`、`docs/data-model.md`、`docs/admin-ui.md`、`AGENTS.md` §2.4

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 先改权威文档，再允许代码依赖新路径和新的二维码库

- [X] T001 [P] 在 `docs/data-model.md` 把 `kb_article.status` 的注释从 `active` 改为 `published` | `retired`，与 `services/java/shop-api-common/src/main/java/com/omino/shopai/enums/PublishStatus.java` 一致。不改列类型，不做数据迁移
- [X] T002 [P] 把 `specs/004-admin-web/contracts/admin-additions.md` 的新路径写入 `docs/api-admin.md`：工作台、会话与消息、工单、知识库、审计、钱包流水、同步游标与重试。写明 `POST /internal/v1/order-sync/{platform}` 只给 Admin 重试用，不进入 `docs/api-internal.md` 的工具白名单。不改已有路径的字段含义
- [X] T003 [P] 在 `AGENTS.md` §2.4 锁定 `qrcode` 的精确版本，写明只用 Canvas 输出、不向服务端要合成图。版本用该包当前稳定版的精确号，不写范围号。本任务不改 `apps/admin-web/package.json`

**Checkpoint**: 文档已覆盖新接口和知识库状态。依赖还没装，Java 还没改

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 分页、前端请求和登录回跳地址。所有页面共用

**⚠️ CRITICAL**: 用户故事开始前必须完成本阶段

- [X] T004 把 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/dto/PageDto.java` 移到 `services/java/shop-api-common/src/main/java/com/omino/shopai/dto/PageDto.java`。字段保持 `list`、`page`、`size`、`total`，`page` 从 1 起。改掉 app 模块里的 import。Admin 不得再定义第二个分页类型，也不得依赖 `shop-api-app`
- [X] T005 [P] 在 `apps/admin-web/vite.config.ts` 把 `/api/admin` 反代到 `http://127.0.0.1:8182`，开发端口保持 `5183`
- [X] T006 [P] 新增 `apps/admin-web/src/api/http.ts`：axios 的 baseURL 为 `/api/admin`，`withCredentials: true`。不设置 `Authorization`，不读取 Cookie
- [X] T007 在 `services/java/shop-api-admin/src/main/resources/application-local.yml` 增加非密钥默认值：管理前端源 `http://127.0.0.1:5183`，APP 根 `http://127.0.0.1:8181`。内部令牌继续用环境里已有的 `AGENT_INTERNAL_TOKEN`，不写进 yml

**Checkpoint**: 前端能把请求发到 Admin API。列表可以返回同一个 `PageDto`

---

## Phase 3: User Story 1 - 用既有身份登录，浏览器不碰令牌 (Priority: P1) 🎯 MVP

**Goal**: 未登录进身份服务，成功后进入工作台。没有账号密码框，浏览器不持有访问令牌

**Independent Test**: 打开 `http://127.0.0.1:5183/` 会去身份服务。登录后停在工作台，地址不是 `/v1/users`。页面、地址栏和可读写存储里没有 access_token。登出后业务页看不到数据

### Implementation for User Story 1

- [X] T008 [US1] 改 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/web/AdminAuthController.java`：会话建立后 302 到 T007 的前端源 `/`。失败不发 `sid_admin`。不在重定向地址里放 access_token
- [X] T009 [US1] 新增 `apps/admin-web/src/router/index.ts` 和 `apps/admin-web/src/views/login-callback.vue`，并在 `apps/admin-web/src/main.ts` 挂上这个路由。未登录访问业务页时，浏览器整页打开 `/api/admin/v1/auth/login`。`/login/callback` 只在失败时提供重新登录，不交换授权码，不出现账号或密码输入框
- [X] T010 [US1] 新增 `apps/admin-web/src/layouts/AdminShell.vue`：顶栏登出调用 `POST /api/admin/v1/auth/logout`。登出后不能再看到业务数据

**Checkpoint**: 能登录并登出。左栏菜单可以还是空的

---

## Phase 4: User Story 2 - 登录后按固定菜单打开每一页 (Priority: P1)

**Goal**: 左栏顺序和路由与 `docs/admin-ui.md` 一致。工作台三个数字来自查询。会话、工单、知识库、审计、同步游标至少能读到真实记录

**Independent Test**: 刷新任一深链仍停在该页。工作台在没有数据时显示 0，而不是空白说明。上述五页在库空时是空表，不是「尚无接口」。库里有记录时能看到至少一行

### Implementation for User Story 2

- [X] T011 [US2] 在 `apps/admin-web/src/router/index.ts` 登记 `docs/admin-ui.md` §2 的全部路由 ID 与路径，包括工作台、订单、同步游标、用户、用户视角、会话、工单、提现、积分三页、海报、版本、特殊配置、公告、站内信、推荐问题、提示词、模型、知识库、审计，以及详情页。业务页套 `apps/admin-web/src/layouts/AdminShell.vue`
- [X] T012 [P] [US2] 在 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/service/ops/AdminDashboardService.java` 和 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/web/AdminDashboardController.java` 实现 `GET /v1/dashboard`。`syncFailures` 是 `last_error` 非空的平台数，只计 `taobao` `jd` `pdd` `vip`。`ticketCount` 是工单行数。`pendingWithdrawals` 是 `status=pending` 的提现数。没有行时三个数都是 0
- [X] T013 [P] [US2] 在 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/service/ops/AdminConversationService.java` 和对应 Controller 实现会话分页 `GET /v1/conversations`（query `userId` `page` `size`）和消息 `GET /v1/conversations/{id}/messages`（时间升序）。只读，不提供修改用户话的方法
- [X] T014 [P] [US2] 在 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/service/ticket/AdminTicketService.java` 和对应 Controller 实现 `GET /v1/tickets`（query `userId` `status` `page` `size`）和 `GET /v1/tickets/{id}`（含处理记录时间线）。本任务不写回复和改状态
- [X] T015 [P] [US2] 在 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/service/kb/AdminKbService.java` 和对应 Controller 实现 `GET /v1/kb/articles`，结果含 `retired`。本任务不写新增和修改
- [X] T016 [P] [US2] 在 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/service/godview/AdminAuditQueryService.java` 和对应 Controller 实现 `GET /v1/audit-logs` 分页、时间倒序。行含 `ssoSub` `action` `resource` `createdAt`。`detail` 不得含密钥、PID、完整收款账号
- [X] T017 [P] [US2] 在 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/service/order/AdminOrderSyncService.java` 和对应 Controller 实现 `GET /v1/order-sync`。固定四行 `taobao` `jd` `pdd` `vip`，字段 `lastSyncCreateTime` `lastSyncUpdateTime` `lastError` `updatedAt`。本任务不写重试
- [X] T018 [US2] 新增只读视图 `apps/admin-web/src/views/dashboard.vue`、`conversations.vue`、`conversation-detail.vue`、`tickets.vue`、`ticket-detail.vue`、`kb.vue`、`audit.vue`、`order-sync.vue`，分别调用 T012 到 T017。加载失败显示失败，不填示例行。会话详情没有编辑用户话的控件

**Checkpoint**: 菜单和只读页可用。工单还不能回复，知识库还不能改，同步还不能重试

---

## Phase 5: User Story 3 - 改提示词、推荐问题和模型权重后能再看到 (Priority: P1)

**Goal**: 三条修改都能再次读取。提示词仍是八条。推荐问题上架不超过 12。模型密钥不回显

**Independent Test**: 改一条提示词、一条推荐问题、一个模型权重后，再用 GET 看到新值。第 13 条上架被拒绝。模型响应没有密钥全文

### Tests for User Story 3

- [X] T019 [US3] 先写失败测试 `services/java/shop-api-admin/src/test/java/com/omino/shopai/admin/ops/SuggestionTest.java`：列表含 `retired`；PATCH 能改 `text` `sort` `status`；已有 12 条 `active` 时再上架返回 `40001` 且该条保持原状态。不提供物理删除

### Implementation for User Story 3

- [X] T020 [US3] 在 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/web/AdminOpsControllers.java` 的推荐问题映射上补 `GET /v1/chat-suggestions` 和 `PATCH /v1/chat-suggestions/{id}`，查询与 12 条限制放在 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/service/ops/AdminDirectory.java` 或同包的一个用例类，使 T019 通过。`text` 去空白后 1–128 字
- [X] T021 [P] [US3] 新增 `apps/admin-web/src/views/chat-suggestions.vue`：可改文案、排序、上下架。没有删除按钮。超过 12 条时显示接口返回的原因
- [X] T022 [P] [US3] 新增 `apps/admin-web/src/views/agent-prompts.vue`：只展示并修改已有八条的 `content`。不能新增、删除或改名。去空白后为空时不提交。保存后重新 GET
- [X] T023 [P] [US3] 新增 `apps/admin-web/src/views/llm-models.vue`：改权重时密钥留空表示不修改。列表只显示 `apiKeySet` 和 `apiKeyMask`，不出现密钥全文

**Checkpoint**: 三处修改都能读回。其他运营页可以还没做

---

## Phase 6: User Story 4 - 查订单和用户时不看见密钥与完整收款账号 (Priority: P2)

**Goal**: 订单表同时有媒体返佣和用户分成。用户列表手机脱敏，详情是完整手机。没有 PID 和完整收款账号

**Independent Test**: 按平台、订单状态、结算状态、用户筛选订单，两列返佣都在。用户列表不是完整手机号，详情是。提现表只有掩码账号

### Implementation for User Story 4

- [X] T024 [US4] 在 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/web/AdminOpsControllers.java` 和 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/service/ops/AdminDirectory.java` 把订单列表改为 `PageDto`，支持 query `userId` `platform` `orderStatus` `settleStatus` `page` `size`，并增加 `GET /v1/orders/{id}`。行内含 `mediaCommission` 与 `userCommission`，没有物流时为空。不返回联盟原始报文
- [X] T025 [US4] 在同一对文件补用户列表 query `keyword` `phone` `inviteCode` `id` `status`，列表手机号脱敏；`GET /v1/users/{id}` 返回完整手机、`inviterId`（没有则为 null）和四平台授权状态，授权对象不含 PID。补 `GET /v1/users/{id}/goods/history`、`favorites`、`collections`、`collections/{cid}`，只读
- [X] T026 [US4] 提现列表改为分页并支持 `userId`。响应只有 `accountMask`，没有完整收款账号。打款失败时页面能显示「打款未成功，冻结额未动」。改 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/web/AdminOpsControllers.java` 和 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/service/wallet/AdminWithdrawalService.java`
- [X] T027 [US4] 新增 `apps/admin-web/src/views/orders.vue`、`order-detail.vue`、`users.vue`、`user-detail.vue`、`withdrawals.vue`。用户详情先接资料、订单、浏览收藏合集。对话、钱包流水、工单栏留到 T041，避免在接口完成前做空壳文案

**Checkpoint**: 订单和用户主路径可查。用户详情还缺对话、流水、工单三栏

---

## Phase 7: User Story 5 - 配置海报、积分、投放和用户视角 (Priority: P2)

**Goal**: 海报在浏览器贴码。积分比例只读。版本、公告、站内信、用户视角按已有契约可用

**Independent Test**: 预览不请求服务端合成图。站内信必须有一个用户。用户视角关闭时签发失败。已开启时只提示成功。两种情况下都不展示凭证，也不打开消费者页面

### Implementation for User Story 5

- [X] T028 [US5] 按 T003 锁定的精确版本，把 `qrcode` 写入 `apps/admin-web/package.json` 并安装。不引入第二个二维码库
- [X] T029 [US5] 在 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/web/AdminOpsControllers.java` 和 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/service/ops/AdminDirectory.java` 补齐积分：套餐 PATCH 与下架 `retired`（被订单引用的不物理删除）、`GET /v1/credit-rate` 只读、`GET /v1/credit-orders`、`GET /v1/credits/ledger`、`POST /v1/credits/adjust`。调账备注为空则 `40001`，并写审计
- [X] T030 [US5] 在 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/web/AdminOpsControllers.java` 和 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/service/ops/AdminDirectory.java` 补 `PATCH /v1/invite-posters/{id}`。`startsAt` 与 `endsAt` 都有时必须 `endsAt` 晚于 `startsAt`，否则 `40001`。不新增预览或合成图接口
- [X] T031 [US5] 在 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/web/AdminOpsControllers.java` 和 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/service/ops/AdminDirectory.java` 补 `GET` 与 `PATCH`：`/v1/releases`、`/v1/runtime-configs`、`/v1/announcements`，以及 `GET /v1/inbox`。`versionCode` 必须是八位日期。同端同渠道同日已有行时禁止再 POST 第二条。站内信 `userId` 为空则 `40001`，不提供群发
- [X] T032 [US5] 新增视图 `apps/admin-web/src/views/credit-packages.vue`、`credit-orders.vue`、`credit-ledger.vue`、`invite-posters.vue`、`invite-poster-edit.vue`、`releases.vue`、`runtime-configs.vue`、`announcements.vue`、`inbox.vue`、`god-view.vue`。海报预览用 `qrcode` 的 Canvas 把占位邀请地址画进矩形。用户视角调用已有 `POST /v1/god-view/tokens`。返回 `40303` 时提示拒绝。签发成功时 `apps/admin-web/src/views/god-view.vue` 和 `apps/admin-web/src/views/user-detail.vue` 都只提示成功，不渲染、不复制凭证，不写入可读写存储，不打开消费者页面

**Checkpoint**: 配置类页面可保存。会话和工单的写操作还没做

---

## Phase 8: User Story 6 - 会话、工单、知识库、审计和同步游标能办完该页的事 (Priority: P2)

**Goal**: 工单回复不关单。知识库可改且下架不删除。同步重试再跑一次既有增量并写审计。用户详情补上对话、流水、工单

**Independent Test**: 回复后状态不变且多一条处理记录。知识库下架后列表仍在、检索不再命中。同步页只有四个平台，`meituan` 被拒绝。审计能看到这次重试且没有密钥

### Tests for User Story 6

- [X] T033 [P] [US6] 先写失败测试 `services/java/shop-api-admin/src/test/java/com/omino/shopai/admin/ticket/TicketCommandTest.java`：回复只追加 `kind=reply` 且 `status` 不变；`status=closed` 必须是一次明确变更并追加 `status_change`；已关闭再回复返回 `40001`
- [X] T034 [P] [US6] 先写失败测试 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/kb/KbPublishedTest.java`：`status=retired` 的文章不被 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/kb/KbImpl.java` 的 `published()` 返回
- [X] T035 [P] [US6] 先写失败测试 `services/java/shop-api-admin/src/test/java/com/omino/shopai/admin/order/OrderSyncRetryTest.java`：平台不是 `taobao` `jd` `pdd` `vip` 时 `40001` 且不调用 APP；合法平台先写 `action=order.sync.retry` 的审计，`detail` 不含密钥、PID、完整收款账号

### Implementation for User Story 6

- [X] T036 [US6] 在 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/service/ticket/AdminTicketService.java` 实现 `POST /v1/tickets/{id}/reply` 和 `PATCH /v1/tickets/{id}`，使 T033 通过。改状态、优先级、处理人时，工单更新与一条 `ticket_log` 同一事务。只回复不改 `status`。状态只用 `open` `processing` `closed`
- [X] T037 [US6] 在 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/service/kb/AdminKbService.java` 实现 `POST /v1/kb/articles` 与 `PATCH /v1/kb/articles/{id}`。新建为 `published`。`status` 只接受 `published` 或 `retired`。不提供删除。使 T034 通过
- [X] T038 [US6] 在 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/web/internal/InternalOrderSyncController.java` 增加 `POST /internal/v1/order-sync/{platform}`，只调用现有 `OrderSyncService.sync`。Admin 在 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/service/order/AdminOrderSyncService.java` 先写审计，再用内部令牌调用该路径，使 T035 通过。Admin 不 import app 模块，不调用联盟 SDK
- [X] T039 [P] [US6] 在 `services/java/shop-api-admin/src/main/java/com/omino/shopai/admin/service/wallet/AdminWalletLedgerService.java` 和对应 Controller 实现 `GET /v1/wallet-ledger`，query `userId` `page` `size`。金额为两位小数字符串。不提供修改历史行的方法
- [X] T040 [US6] 给 `apps/admin-web/src/views/ticket-detail.vue`、`kb.vue`、`order-sync.vue` 接上回复、改状态、编辑、下架和重试。回复成功后状态显示不变。同步页没有美团一行
- [X] T041 [US6] 在 `apps/admin-web/src/views/user-detail.vue` 接上对话、钱包流水、工单三栏，请求都带当前用户 id。浏览、收藏、合集仍然没有删除按钮

**Checkpoint**: 规格里的六条故事都能单独演示。用户详情不再缺栏

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: 新接口的文档注解和整条 quickstart

- [X] T042 给本功能新增的每个 Controller 方法补 `@Operation`，`summary` 写这个动作做什么，不写路径。类上保留 `@Tag`。方法正上方写 Javadoc，说明参数和返回值。涉及工单事务和重试的分支写行注释，说明这一步在保证什么
- [X] T043 按 `specs/004-admin-web/quickstart.md` 走一遍登录、菜单、三处读回、订单与用户、五页操作、海报预览和用户视角关闭。失败则修到通过为止
- [X] T044 [P] 在 `apps/admin-web/src` 确认没有账号密码表单，也没有读取或保存 access_token 的代码

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，T001、T002、T003 可并行
- **Foundational (Phase 2)**: 依赖 Setup。未完成不得开始用户故事
- **User Story 1（Phase 3）**: 依赖 Foundational。这是 MVP
- **User Story 2（Phase 4）**: 依赖 User Story 1 的壳和路由
- **User Story 3（Phase 5）**: 依赖 Foundational 的 http 客户端。可与 User Story 2 并行，因为改的是另一批文件
- **User Story 4（Phase 6）**: 依赖 User Story 1 的壳。订单和资料不依赖 User Story 2
- **User Story 5（Phase 7）**: 依赖 T003 和 T028 的版本锁定。可与 User Story 4 并行
- **User Story 6（Phase 8）**: 依赖 User Story 2 已建的只读服务和视图。T041 依赖 T039 和 User Story 2 的会话、工单列表
- **Polish**: 依赖要验收的故事全部完成

### User Story Dependencies

- **User Story 1 (P1)**: Foundational 之后即可开始
- **User Story 2 (P1)**: 需要 User Story 1 的布局。只读接口之间可并行
- **User Story 3 (P1)**: 不依赖 User Story 2 的数据页
- **User Story 4 (P2)**: 不依赖 User Story 3。用户详情后三栏依赖 User Story 6
- **User Story 5 (P2)**: 不依赖 User Story 4 和 User Story 6
- **User Story 6 (P2)**: 在 User Story 2 的只读页上补写操作

### Within Each User Story

- 点名的测试先写，并在实现前失败
- 服务先于 Controller 的新路径，视图最后接上
- 同一 Java 文件上的任务不标并行

### Parallel Opportunities

- T001、T002、T003 可并行
- T005 与 T006 可并行
- T012 到 T017 可并行（不同服务类）
- T021、T022、T023 可并行
- T024、T025、T026 都改 `AdminOpsControllers.java`，顺序做
- T029、T030、T031 都改 `AdminOpsControllers.java`，顺序做
- T033、T034、T035 可并行

---

## Parallel Example: User Story 2

```bash
# 只读接口分属不同类，可一起做：
Task: "AdminDashboardService 与 GET /v1/dashboard"
Task: "AdminConversationService 与会话、消息 GET"
Task: "AdminTicketService 的两个 GET"
Task: "AdminKbService 的列表 GET"
Task: "AdminAuditQueryService 与 GET /v1/audit-logs"
Task: "AdminOrderSyncService 的 GET /v1/order-sync"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1 和 Phase 2
2. 完成 Phase 3，登录后能进工作台并登出
3. 停下来按 User Story 1 的独立测试验收

### Incremental Delivery

1. 加上 User Story 2：菜单和只读数据
2. 加上 User Story 3：三处可核对的修改
3. 再加上 User Story 4、5、6
4. 最后跑 quickstart

### Parallel Team Strategy

Foundational 完成后：一人做 User Story 2 的只读接口，另一人做 User Story 3 的推荐问题与两个配置页。User Story 6 等 User Story 2 的服务类落地后再改那些文件。

---

## Notes

- 不新增表，不让 Admin import `shop-api-app`
- 新接口先有 `docs/api-admin.md` 的条目，再写 Controller
- 分页只用 common 里的 `PageDto`
- 模型密钥、联盟 PID、完整收款账号不出现在响应和表格
- 提交按任务或一组相关任务进行，不要把六个故事捆成一次提交
