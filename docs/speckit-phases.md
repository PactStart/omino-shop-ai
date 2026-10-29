# Spec Kit 分阶段实现（给 AI 的操作手册）

> **本文件不是产品规范。** 字段、路径、意图、SSE、表结构以 [`AGENTS.md`](../AGENTS.md) 与 `docs/conversation.md`、`docs/api-*.md`、`docs/data-model.md` 为准。下文残留的 §4.x / §6.x 指这些文档里仍保留的原编号标题。Agent 运行时见 [`agent.md`](./agent.md)。
>
> **不要实施本文以外的范围。** 一次只做当前阶段对应的 **一个** Spec Kit 功能。不要把整份 `AGENTS.md` 一次性丢进 `/speckit-specify`。不要对同一功能再跑 OpenSpec（`/opsx-*`）。

---

## 0. 新会话开场（整段复制）

把下面整块贴给 Cursor Agent（可再加一句「当前做 F0X」）：

```text
你在仓库 omino-shop-ai。动手前：
1. 在 docs/product-input.md 主表末尾追加一行（时间 Asia/Shanghai YYYY-MM-DD HH:mm、原始输入、意图/原因）。
2. 读 AGENTS.md（权威）和 docs/speckit-phases.md（本阶段范围）。
3. 只做我指定的那一个阶段。mall-guide 只读、禁止复制代码/包名/表名。Admin 身份只走 omino-sso（本阶段若未排到 SSO 则禁止自建管理员密码表）。
4. 主库 PostgreSQL+pgvector，不用 MySQL。APP 与 Admin 是同一套 Java 两个产出物，前缀 /api/app 与 /api/admin。联盟密钥与转链只在 Java。对话 Android/iOS/H5 → Java SSE → Agent；Tool 只调 app 的 /internal/v1。禁止 Flutter。
5. 流程：/speckit-git-feature → /speckit-specify（只用该阶段「Specify 粘贴稿」）→ 我审 spec → /speckit-plan → 我审 plan → /speckit-tasks → /speckit-implement。
6. 实现须符合 AGENTS.md 目录与分层；测不过不算完成。改行为先改 AGENTS.md。
```

每个阶段建议 **新开对话**，避免上下文把后一阶段范围带进来。

---

## 1. 怎么用 Spec Kit（每个阶段都一样）

```
/speckit-constitution     ← 仅 Wave 0，且当前仍是模板时必须先做
/speckit-git-feature      ← specify 前强制开功能分支，不要在 main 上 specify
/speckit-specify          ← 参数 = 该阶段「Specify 粘贴稿」全文
（你审 spec：approve）
/speckit-plan
（你审 plan：approve）
/speckit-tasks
/speckit-implement
```

可选：specify 后 `/speckit-clarify`；plan 后 `/speckit-analyze`、`/speckit-critique-run`；implement 后 `/speckit-gates-verify`。

**粒度**：一个阶段 = 一条可演示的竖切能力，不是一个 Java 类，也不是「整个 APP」。specify 写用户可见行为与验收，plan/tasks 才拆文件。

**依赖**：未勾选「依赖阶段」完成前，禁止开下一阶段 specify。

**分支建议 slug**（`/speckit-git-feature` 的短名）：与下表 `slug` 一致，例如 `001-scaffold`。

---

## 2. 总图：三波，一次一个功能

| 波次 | 目标 | 阶段 | 建议日历 |
|---|---|---|---|
| Wave 0 | 宪章可执行 | C0 | 0.5 天 |
| Wave 1（P0 Demo） | **H5** 手机号验证码登录，能对话出假商品卡、刷新不断、能点停止 | F01–F06 | 约 1 周（全力） |
| Wave 2（能买能查） | **四平台**真适配器可同一波联调 + HITL + 订单同步 + 查单 | F07–F10 | 其后若干周 |
| Wave 3（规范全量） | 其余意图、钱包、邀请、工单、原生双端、四登录、Admin SSO、运营配置 | F11–F22 | 更后 |

Wave 1 是「一周能交」的上限。Wave 2/3 **不要**塞进第一次 specify。

```
C0 宪章
 → F01 五端空跑 + PG/Redis/Flyway
 → F02 手机号验证码登录 + app_user（创蓝；local 可 mock）
 → F03 对话 run：POST turns / Redis Stream / SSE 泵 / cancel（Worker 可先写假事件）
 → F04 Agent 图 + shop.search + search_goods mock（>6 条出合集卡）
 → F05 详情 + 去购买（未授权 40301 NEED_AUTH；转链可假）+ 浏览写入
 → F06 **H5** 对话页（卡、合集卡、停止、断线续传）；原生放到 Wave 3 `023-native-chat`
 → F07 四平台真联盟适配器（同一波，四个 Client 类）
 → F08 HITL 联盟授权
 → F09 订单游标同步 + order.query
 → F10 me.* 只读
 → F11 钱包流水 + 提现申请（P0 页，对话可后做）
 → F12 邀请码 + 海报模板（端上贴码）+ 落地页
 → F20 官网 www
 → F13 知识库 + 工单
 → F14 四平台适配器硬化
 → F23 原生 Android/iOS 对话页
 → F15 语音转写 / 图搜 / 粘贴链接
 → F16 微信 Apple Google 登录补齐
 → F17 Admin OIDC + 运营页
 → F18 Langfuse 生产强制与多副本验收
```

---

## 3. Wave 0 — 宪章

### C0 — 补完 Spec Kit constitution

**slug：** 无功能分支亦可；在现有分支改 `.specify/memory/constitution.md`。

**做：** 把模板改成本项目原则：TDD、安全（密钥不进客户端、Admin 只 SSO）、测试门禁、与 `AGENTS.md` §7 一致。不写业务代码。

**Specify：** 本阶段 **不要** `/speckit-specify`。对 Agent 说：

```text
只做 docs/speckit-phases.md 的 C0：补完 .specify/memory/constitution.md，对齐 AGENTS.md §7。不写业务代码、不搭脚手架。
```

**验收：** constitution 不再是占位模板；`/speckit-doctor` 能跑且无「未初始化」类硬伤。

---

## 4. Wave 1 — P0 Demo（F01–F06）

Wave 1 **允许**：mock 商品、测试 JWT、去购买返回 NEED_AUTH 或假链接、Admin 仅空壳+health。  
Wave 1 **禁止**：真联盟 SDK、订单同步、钱包、四登录、语音图搜、生产 SSO。

### F01 — 五端空跑与基础设施

**slug：** `001-scaffold`  
**依赖：** C0

**用户可见：** 本地一条 compose 起来 PG（pgvector）+ Redis；Java APP `:8081`、Admin `:8082` 的 actuator health；Agent `:8090/health`；Android `./gradlew :app:assembleDebug`、iOS 工程能打开；Admin-web / www / land `npm run dev` 能开空白页。

**目录必须一次对齐 `AGENTS.md` §3**（包名 `com.omino.shopai`，模块 `shop-api` / `shop-api-common` / `shop-api-app` / `shop-api-admin`）。Flyway 可先建扩展与空迁移，**本阶段不必把 §6 全部表建完**，但必须能跑迁移。

**Specify 粘贴稿：**

```text
功能：omino-shop-ai 脚手架（F01 / 001-scaffold）。

做：
- deploy/docker-compose.local.yml + deploy/env/local.env.example：PostgreSQL 16 + pgvector、Redis 7.4；不要 MySQL。
- services/java：Maven parent 3.9.9，Boot 3.4.11，JDK 21；shop-api-common / shop-api-app / shop-api-admin；APP context-path /api/app 端口 8081；Admin /api/admin 端口 8082；Actuator health。
- Flyway 11.3.1 + PostgreSQL，迁移放 shop-api-app/resources/db/migration。
- 统一响应信封骨架（AGENTS.md §5）：code/message/data/traceId/ts。
- services/agent：uv、fastapi 0.115.6、GET /health，不对外公网。
- apps/admin-web：Vue 3.5.13 + Vite 6.3.5 + TS + Pinia + Vue Router + Element Plus 2.9.1 空壳。
- apps/android、apps/ios：空壳能配 API_BASE。apps/www、apps/land：Vite 空壳。
- README 只写 AGENTS.md §8 已有启动命令，不要另发明。

不做：登录、对话、联盟、SSO 业务、复制 mall-guide。

验收：compose up 后三个 health 200；mvn 能编译两个模块。
权威：AGENTS.md §2 §3 §8 §9。
```

**验收命令（实现后）：** compose up；curl APP/Admin actuator health；curl Agent `/health`。

---

### F02 — 手机号验证码登录

**slug：** `002-app-auth`  
**依赖：** F01

**用户可见：** 消费者 **H5** 用手机号收验证码拿到 APP JWT。后续请求 `Authorization: Bearer`。落 `app_user`（`provider=phone`）。未登录 `40100`。`local` 可用 `SMS_PROVIDER=mock`。邀请码可选，绑定即 `app_invite.status=valid`。

**Specify 粘贴稿：**

```text
功能：APP 手机号验证码登录（F02 / 002-app-auth）。对齐 docs/api-app.md §4.6.1。

做：
- Flyway：app_user / app_user_identity，字段按 docs/data-model.md §6.1–6.2（PG；status 用 SMALLINT）。
- POST /api/app/v1/auth/sms/send、/auth/sms/login；签发 JWT（Nimbus HS256，JWT_ACCESS_SECRET）。
- 短信：创蓝字段对齐 omino-sso（TEMPLATE_ID + 硬编码 param1）；local 允许 mock。禁止从零手写 SMTP/短信协议。
- GET /api/app/v1/me 最小字段；无 token → 40100。
- 可选 inviteCode：新用户绑定上级且立即 valid。禁止自己邀自己。
- 禁止自建管理员密码表。禁止 APP 接 omino-sso。

不做：微信/Apple/Google（F16）、真创蓝账号可到 test 再配。

验收：H5 发码→登录→me；无 token 40100。
权威：AGENTS.md §2.1、docs/api-app.md §4.6.1、§5、data-model §6.1。
```

**验收：** H5 登录 → me；无 token 被拒。

---

### F03 — 对话 run 与 Redis SSE（可先无真 Agent）

**slug：** `003-chat-run`  
**依赖：** F02

**用户可见：** `POST /api/app/v1/chat/turns` 立刻返回 `conversationId/runId/clientMsgId`；`GET .../runs/{runId}/events` SSE；刷新带 `Last-Event-ID` 能续；`POST .../cancel` 后出现 `cancelled` 终态。断开 SSE **不得**停 Worker。

本阶段 Worker **可以**往 Redis Stream 写假 `text_delta` + `done`，不强制接 Python。事件名必须是 `AGENTS.md` §4.1 那张表。

**Specify 粘贴稿：**

```text
功能：对话生成与 SSE 解耦（F03 / 003-chat-run）。

做（契约逐字对齐 AGENTS.md §4.1、§4.1.1、§4.1.2）：
- POST /api/app/v1/chat/turns：JWT；clientMsgId 幂等；空 conversationId 则创建；立刻返回 runId。表 conversation / conversation_message 按 §6.5。
- Worker 任意副本：事件只 XADD 到 Redis Stream chat:run:{runId}；本阶段可用假事件，但形状要真。
- GET /api/app/v1/chat/runs/{runId}/events：SSE，id 用 Redis 消息 ID，泵间隔 ≤100ms；断开连接禁止取消 Worker。
- POST /api/app/v1/chat/runs/{runId}/cancel：SET chat:run:{runId}:cancel；Worker abort；XADD cancelled。刷新 ≠ 取消。
- ChatTurnService / ChatRunWorker / ChatEventPump / ChatCancelService 分层，Controller 不堆业务。

不做：LangGraph、搜品、HITL interrupt、把 Agent 流直连 APP。

验收：turns → events 收到 done；断开重连能续；cancel 收到 cancelled；再 turns 同一 clientMsgId 返回同一 runId。
权威：AGENTS.md §4.1。
```

**验收：** 上述三条路径用 curl 或测试打通。

---

### F04 — Agent 图 + mock 搜品

**slug：** `004-agent-shop-search`  
**依赖：** F03

**用户可见：** 用户说「我要买纸巾」，SSE 出现 `intent`（`shop.search`）和商品卡或 **合集卡**（mock 结果 >6 条时只出 `collection_card`），数据来自 Java `POST /internal/v1/goods/search`，不是 LLM 编造。

**Specify 粘贴稿：**

```text
功能：Agent 搜品竖切（F04 / 004-agent-shop-search）。

做：
- Agent POST /internal/v1/graph/invoke（仅内网，X-Internal-Token），SSE 事件名与 §4.1 相同。见 AGENTS.md §4.4 与 docs/agent.md。
- 一张 StateGraph：ingest → intent_router → shop.search（槽位不足则 clarify）→ reply_sse。confidence < 0.6 不调搜品。
- Tool search_goods → Java POST /internal/v1/goods/search；mock 适配器。条数 >6 时 Java 写 goods_collection，返回 collectionId + 预览≤4；Agent 发 collection_card，禁止再刷 product_cards。LLM 用 DeepSeek（langchain-openai 兼容基址）。禁止 Agent 持联盟密钥。
- Java ChatRunWorker 改为消费 Agent SSE 再 XADD Redis（替换 F03 假事件）。internal 禁止再调 Agent。
- Checkpointer PostgresSaver，thread_id=conversationId；本阶段可最小配置。
- 本阶段不做其它一级意图（chitchat 可返回短文本以免卡死）。

不做：真联盟、HITL、图搜、转链、LangSmith。Langfuse 可空配置关闭。

验收：带 JWT 发「我要买纸巾」收到 intent；≤6 有卡，>6 仅合集卡；字段来自 Java mock。 GET /goods/collections/{id} 能列出未移除商品并可 DELETE 一项。
权威：AGENTS.md §4.0 §4.4 §4.5、docs/agent.md。
```

**验收：** 真走 Agent，卡上的价格不是模型瞎编。

---

### F05 — 详情与去购买（授权门）

**slug：** `005-goods-detail-buy`  
**依赖：** F04

**用户可见：** 点卡调 `POST /api/app/v1/goods/detail`（不经 Agent）；「去购买」`POST /api/app/v1/goods/convert-link`：未授权 → HTTP 可 200 但 `code=40301`，`error.reason=NEED_AUTH`；已授权本阶段可用测试开关或种子数据，返回假 `clickUrl`/`deeplink`（**禁止**把 PID 下发客户端）。

**Specify 粘贴稿：**

```text
功能：商品详情与去购买门闩（F05 / 005-goods-detail-buy）。

做：对齐 AGENTS.md §4.2 §4.3。
- detail：Redis 短缓存 → 未命中走 mock 适配器 → 瘦写入 goods_snapshot，禁止存平台原始大 JSON。成功写 user_event.kind=browse。
- GET /goods/history、收藏 POST/GET/DELETE（docs/api-app.md §4.6.2）。
- convert-link：校验 platform_authorization；未授权 40301 NEED_AUTH；preferApp 看 app_user_installed_app（本阶段可默认未安装只返 fallbackHttpUrl）；转链只在 Java；写 click_event。不实现 /buy、/promotion-link。
- 对话卡片禁止批量转链。

不做：真联盟转链 SDK、一次接齐四平台、原生详情页可放到 F06 一起或本阶段最小 Web/测试客户端。不做美团。

验收：detail 返回统一卡片；未授权 convert-link 为 40301；响应无 PID/密钥。
权威：AGENTS.md §4.2 §4.3 §6.7 §6.10 §6.11。
```

---

### F06 — 消费者 H5 对话页（Demo）

**slug：** `006-h5-chat`  
**依赖：** F03（演示依赖 F04 出卡、F05 进详情）

**用户可见：** H5 手机号登录后对话；流式字；商品卡 / 合集卡；点卡进详情或合集页；停止走 cancel；刷新可续。浏览/收藏入口在「我的」。

**Specify 粘贴稿：**

```text
功能：消费者 H5 Demo（F06 / 006-h5-chat）。对齐 AGENTS.md §2.4.2、docs/app-ui.md。

做：
- apps/h5：loginPhone、chat、goodsDetail、goodsCollection、goodsHistory、goodsFavorites。禁止 Element Plus、禁止 Flutter。
- 头：X-Client-Type=h5、X-App-Channel=web。
- SSE：text_delta / product_card(s) / collection_card / clarify / error / done / cancelled。
- 停止仅 POST cancel。合集页可移除商品。详情可收藏。
- 去购买 NEED_AUTH 提示即可（授权 F08）。

不做：原生双端（Wave 3 `023-native-chat`）、微信登录、友盟/极光（原生阶段再锁版本）。

验收：浏览器走完「验证码登录 → 买纸巾 → 见卡或合集 → 详情/合集移除 → 停止一次」。
权威：AGENTS.md §2.4.2、docs/conversation.md、docs/api-app.md。
```

**Wave 1 总验收：** 上述 H5 路径 + curl 证明 Redis 续传与 cancel。到此可叫 Demo，**不要**宣称可上架。

原生 Android/iOS 对话页：Wave 3 slug `023-native-chat`，页面 ID 与 H5 相同。

---

## 5. Wave 2 — 四平台联调（F07–F10）

未完成 Wave 1 总验收前不要 specify 本波。F07 **允许同一功能里接 taobao/jd/pdd/vip 四个 Client**（仍禁止复制 mall-guide、禁止 service 直接 new SDK）。美团不做。

### F07 — 四平台联盟适配器

**slug：** `007-platform-adapters`  
**依赖：** F05

四个平台可一起联调：`TaobaoGoodsClient` / `JdGoodsClient` / `PddGoodsClient` / `VipGoodsClient`。搜品/详情走真接口，失败 `50010`。密钥只环境变量。Nacos 可按平台 enable。

**Specify 粘贴稿：**

```text
功能：接入四平台联盟适配器（F07）。平台码仅 taobao jd pdd vip。对齐 docs/api-internal.md search/detail 与 AGENTS.md adapter/platform。禁止密钥下发客户端。禁止复制 mall-guide。可同一 PR，但每个平台独立 Client 类。
验收：每平台至少一条真实测试商品能搜到且详情进 Redis。未 enable 的平台明确未实现，不得静默编数据。
```

### F08 — HITL 联盟授权

**slug：** `008-union-hitl-auth`  
**依赖：** F07、F04（图上 interrupt）

`auth.grant` / `auth.revoke`：SSE `auth_card`/`confirm_card` 后 **本 run 结束**；用户完成后再 POST turns 带 `hitl`。禁止用 `interrupt()` 模拟停止生成。回调写 `platform_authorization`。`start_authorization` / `revoke_authorization` 仅 internal。

**Specify 粘贴稿：**

```text
功能：联盟授权 HITL（F08）。对齐 AGENTS.md §4.0 auth.*、§4.1 HITL、§4.5 start/revoke、docs/agent.md interrupt ≠ cancel。
验收：未授权 convert-link=NEED_AUTH；对话授权出卡；回调后 status=authorized；再转链不再 40301（转链仍可以是该平台真实或受控测试链）。
```

### F09 — 订单增量同步与查单

**slug：** `009-order-sync-query`  
**依赖：** F07

Quartz 默认 5 分钟；`order_sync_cursor` 按平台；统一 `app_order`。订单状态 `order_status` 六值，结算状态 `settle_status` 另列。完成且待结算的单在同步事务里给下单用户写一条 `wallet_ledger`，回填 `wallet_ledger_id`。对话 `order.query` 只读 internal，无物流则 `null`，返佣只说 `userCommission`，禁止编造。

**Specify 粘贴稿：**

```text
功能：订单游标同步 + 用户查单（F09）。对齐 data-model §6.8 §6.9 §6.13、api-app §4.10、internal list_orders/get_order。订单状态与结算状态分开；分成比例走 Nacos commission.user-share-rate；已完成且待结算才入账，一单一条流水。覆盖 F07 已接平台（四平台可同一阶段，每平台独立 cursor 行）。
验收：同步任务能写入/更新 app_order；完成单入账且重复同步不双写钱包；对话问订单不编造物流，也不说出媒体佣金。
```

### F10 — 我是谁 / 授权列表 / 月统计

**slug：** `010-me-stats`  
**依赖：** F02、F08、F09

`get_me`、`list_authorizations`、`get_order_stats`、`get_wallet`（钱包可先全 0）。意图 `me.profile` / `me.auth_list` / `me.stats`。

**Specify 粘贴稿：**

```text
功能：账户只读意图（F10）。对齐 AGENTS.md §4.0 me.* 与 §4.5 对应 Tool。禁止 Agent 写联盟。
验收：对话「我是谁」「授权了哪些」「这月几单」数字来自 Java。
```

---

## 6. Wave 3 — 规范全量（F11–F23）

每项单独 Spec Kit。下面只给范围，specify 时把对应 `AGENTS.md` 章节贴进粘贴稿，并写清 **不做**。

| slug | 功能 | 对齐 | 本阶段明确不做 |
|---|---|---|---|
| `011-wallet-withdraw` | 钱包流水、提现申请、Admin 审核 | §6.13–6.14、Admin 提现 API | 积分套餐支付（F19）；自营商品购物车 |
| `019-credits-pay` | Token 明细、积分账户、套餐、支付单（微信/支付宝/**钱包余额**）、SSE `credit_card`；比例在 Nacos | api-app 4.6、data-model 6.18–6.23 | 积分提现或转回钱包；Agent 扣费；`credit_rate` 表；Apple IAP |
| `012-affiliate` | 邀请码、`app_user.inviter_id`、app_invite、海报模板（生效期）+ 端上贴码滑动、earn.invite、落地页拉起 | api-app 4.7、data-model 6.1 / 6.15 | 复杂分销树；上级第二笔分成；服务端合成成品图 |
| `020-public-web` | 官网 www + 落地页 land `/i/:code` | §2.4.1 | Admin、Element Plus |
| `021-h5-god-view` | H5 剩余页对齐 + 上帝视角 | §2.4.2、api-admin god-view | 生产默认开启上帝视角；Element Plus；对话主路径已在 F06 |
| `022-app-ops` | 版本记录（Android 渠道包）、runtime-config、公告、站内信、设置页 | §4.8–4.10 §6.24–6.27 | 群发站内信当公告 |
| `013-kb-ticket` | search_kb miss、建工单、Admin 工单 | §6.12 §6.16、kb.faq/ticket.* | 人工实时坐席、MCP（可后换 search_kb） |
| `014-platforms-harden` | 四平台适配器补强（限流、失败码、enable） | adapter + cursor | 美团；新平台码 |
| `015-multimodal` | voice_transcript / image / goods_url | §4.0 shop.image/shop.link、上传接口 | 端上语音模型 |
| `016-app-logins` | 微信开放平台 App / Apple / Google；友盟+极光随原生 | §6.2、§9.2 | APP 接 SSO；小程序当主登录 |
| `017-admin-sso` | Admin OIDC+PKCE S256 BFF、运营页（含用户列表/详情用户视角菜单） | §2.4、admin-ui、api-admin 用户 | 后台微信登录、自建 admin 密码 |
| `024-chat-ops` | 新对话推荐问题、八条 Agent 提示词、多模型权重分流 | data-model §6.28–6.30、api-admin、api-internal invoke、admin-ui | Langfuse 当提示词数据源；一次模型调用换一个模型；运营自建 prompt key |
| `018-obs-ha` | Langfuse prod 强制、多副本 cancel/SSE 验收 | §4.1、§9.4 | LangSmith、K8s 非目标可仍用 compose |
| `023-native-chat` | Android + iOS 对话/详情/合集/登录（同 app-ui） | §2.3、app-ui | Flutter；本阶段不验收商店上架 |

`014` 只做 F07 已接四平台的硬化，不要加第五平台。

### F21 — H5 上帝视角与剩余页

**slug：** `021-h5-god-view`  
**依赖：** F06；签发走 Admin，可与 F17 同迭代，local 开 `GOD_VIEW_ENABLED`

**Specify 粘贴稿：**

```text
功能：H5 上帝视角与剩余页面（F21）。对话主路径已在 F06。对齐 AGENTS.md §2.4.2、api-app 客户端头、api-admin god-view。禁止 Element Plus。上帝视角仅 Admin POST /god-view/tokens，JWT gv=1，顶栏常驻不可关；prod 默认关闭。gv=1 禁止注销。H5 不是落地页 apps/land。
验收：Admin 签发后打开 H5 看到指定用户且顶栏有上帝视角条；订单/设置/邀请与原生页面 ID 一致。
```

### F22 — 版本 / 渠道配置 / 公告 / 站内信 / 设置与订单筛选

**slug：** `022-app-ops`  
**依赖：** F02、F09、F17

**Specify 粘贴稿：**

```text
功能：运营配置与设置页（F22）。对齐 docs/api-app.md §4.8–4.10、docs/data-model.md §6.24–6.27。Android 按渠道分行 app_release；version_code 为 yyyyMMdd 整数，检查更新比这个。runtime-config 按端/渠道/版本浅合并。公告可定向端/渠道/版本。inbox 只点对点，禁止当群发。设置：风格与字号仅本地；协议走 GET /legal；推送 PATCH /me/push；注销 POST /me/delete；检查更新；退出登录。订单列表 platform+status 筛选；再次购买进商品详情，禁止行内转链。
验收：渠道包旧版能 force/optional；某渠道某版本 payload 生效；公告命中范围；私信只该用户可见；订单筛选与再次购买进详情。
```

---

## 7. 每个阶段对 AI 的硬约束（可再复制）

```text
硬约束：
- 先改 AGENTS.md 再改代码（本阶段若要补登录路径等缺口，先改规范）。
- 先在 docs/product-input.md 追加一行再办事。
- mall-guide 只读，禁止复制源码、Mapper、表名、包名。
- 管理后台身份 = omino-sso Authorization Code + PKCE S256；未排到 F17 时 Admin 可以无登录空壳，禁止自建管理员账号密码表。
- 一套 Java 两个产出物，禁止 app 与 admin 互相 import；/internal/v1 只挂 app 进程。
- 密钥、PID、relationId 只在 Java 环境变量。
- 主库只有 PostgreSQL+pgvector。
- 意图用点分名（shop.search 等）；SSE 事件名锁定。
- 停止生成走 Redis cancel + abort HTTP；禁止 interrupt() 当停止；断开 SSE 禁止停 Worker。
- 积分扣费只在 Java；Agent 只报 usage。积分与 wallet 分户。
- 不引入 AGENTS.md 未锁定的依赖版本；升级先改规范表。
- 本阶段「不做」列表里的任何能力都不要预留空接口/空表/TODO 当完成。
```

---

## 8. 你怎么点下一步（人类清单）

1. 确认 C0 constitution 已不是模板。  
2. 新对话，贴 §0 开场 +「开始 F01」，再执行 §1 命令序列。  
3. spec / plan 两道人工门必须看 **范围是否膨胀**（最常见失败：F01 顺手写登录和搜品）。  
4. F01–F06（H5 Demo）验收后再开 F07（四平台可一起）。  
5. 同一功能不要 `/opsx-propose`。

当前仓库还没有 `specs/<feature>/` 业务功能目录；**第一个真正的 `/speckit-specify` 应从 F01 开始**（C0 除外）。
