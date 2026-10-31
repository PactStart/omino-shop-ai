# Spec Kit 分阶段实现（给 AI 的操作手册）

> **本文件不是产品规范。** 字段、路径、意图、SSE、表结构以 [`AGENTS.md`](../AGENTS.md) 与 `docs/conversation.md`、`docs/api-*.md`、`docs/data-model.md`、`docs/app-ui.md`、`docs/admin-ui.md`、`docs/agent.md` 为准。
>
> **一次只做下面 8 个 feat 里的一个。** 不要把整份 `AGENTS.md` 丢进 `/speckit-specify`。不要对同一功能再跑 OpenSpec（`/opsx-*`）。
>
> 原先 F01–F24 的竖切收进这些 feat。feat 里面的「切片」只是 `tasks.md` 的施工顺序，**不再各开一次 Spec Kit**。

---

## 0. 新会话开场（整段复制）

```text
你在仓库 omino-shop-ai。动手前：
1. 在 docs/product-input.md 主表末尾追加一行（时间 Asia/Shanghai YYYY-MM-DD HH:mm、原始输入、意图/原因）。
2. 读 AGENTS.md（权威）和 docs/speckit-phases.md（只做我点名的那一个 feat）。
3. mall-guide 只读、禁止复制代码/包名/表名。Admin 身份只走 omino-sso。未排到 feat 2 的 SSO 完成前，禁止自建管理员密码表。
4. 主库 PostgreSQL+pgvector，不用 MySQL。APP 与 Admin 是同一套 Java 两个产出物。联盟密钥与转链只在 Java。对话 Android/iOS/H5 → Java SSE → Agent；Tool 只调 app 的 /internal/v1。禁止 Flutter。
5. 流程：/speckit-git-feature → /speckit-specify（只用该 feat「Specify 粘贴稿」）→ 我审 spec → /speckit-plan → 我审 plan → /speckit-tasks → /speckit-implement。
6. 实现须符合 AGENTS.md 目录与分层；测不过不算完成。改行为先改对应权威文档。
```

每个 feat 建议 **新开对话**。

---

## 1. 怎么用 Spec Kit

```
/speckit-git-feature      ← specify 前开功能分支，不要在 main 上 specify
/speckit-specify          ← 参数 = 该 feat「Specify 粘贴稿」全文
（你审 spec：approve）
/speckit-plan
（你审 plan：approve）
/speckit-tasks
/speckit-implement
```

可选：specify 后 `/speckit-clarify`；plan 后 `/speckit-analyze`；implement 后 `/speckit-gates-verify`。

若 `.specify/memory/constitution.md` 仍是模板，在 **feat 1 开工前**先改完，对齐 `AGENTS.md` §7。这一步不单独占一个 feat，也不跑 `/speckit-specify`。

**依赖：** 未勾选「依赖」完成前，禁止开下一个 feat 的 specify。feat 8 例外，见下表。

---

## 2. 八个 feat

按端切，不按每个接口切。Java 的 HTTP 全部在 feat 2；真实联盟出站只在 feat 3。管理后台页面在 feat 4。Agent 只消费 Java 已有的 `/internal/v1`，不直连联盟。H5 是消费者端验收面。iOS 与 Android 同一套页面契约，放在同一个 feat，不把 Android 再拆出去。

| # | slug | 名称 | 依赖 | 用户能看到什么 |
| --- | --- | --- | --- | --- |
| 1 | `001-scaffold` | 搭建项目骨架，空跑 | constitution 已不是模板 | 本地 compose、两个 Java health、Agent health、各端空壳能启动 |
| 2 | `002-java-api` | Java 端 | feat 1 | APP / Admin / internal 的 HTTP 与表、同步、扣费、结算可用 curl 或测试打通 |
| 3 | `003-union-sdk` | 官方联盟 SDK | feat 2 | 四个平台能真实搜品、看详情、转链、按游标拉订单 |
| 4 | `004-admin-web` | 后台管理页面 | feat 3 | 运营用 SSO 登录，按 `admin-ui.md` 操作已有接口 |
| 5 | `005-agent` | Python Agent | feat 3 | 一句购物话得到意图、商品卡或合集卡；授权 HITL；查单与账户数字来自 Java |
| 6 | `006-h5` | 消费者 H5 | feat 5 | 手机号登录后走完 `app-ui.md` 的 H5 页面 |
| 7 | `007-ios` | iOS（Android 同契约） | feat 6 的页面契约 | iOS 与 Android 原生对话、详情、订单、我的；不验收上架 |
| 8 | `008-www-land` | 官网与落地页 | feat 1；落地页邀请预览依赖 feat 2 的公开字段 | `apps/www` 品牌页，`apps/land` 的 `/i/{inviteCode}` 与 `/download` |

```
feat 1 骨架
  → feat 2 Java
       → feat 3 官方联盟 SDK
            → feat 4 管理页面
            → feat 5 Agent
                 → feat 6 H5
                      → feat 7 iOS + Android
feat 1 完成后，feat 8 的官网可以并行；落地页若要调公开预览，等 feat 2 对应接口。
```

feat 2 内部按这个顺序写 `tasks.md`，前一切片没验收不要跳到后一切片：

1. 信封、`app_user` / `inviter_id`、手机号验证码登录（local 可 mock）、JWT。
2. 对话 run：POST turns、Redis Stream、SSE 泵、cancel。Worker 在 feat 5 接上之前可以写符合事件名的假事件。
3. 商品详情、瘦快照（含销量、评价数、商品链接）、转链门闩、浏览 / 收藏 / 合集。
4. 淘宝、京东、拼多多、唯品会四个适配器壳。密钥只在环境变量。真实出站不在本 feat，见 feat 3。
5. 订单游标同步、`order_status` / `settle_status`、完成单给下单用户写一条钱包流水、提现。
6. 积分账户、套餐、微信 / 支付宝 / 钱包余额支付；扣费只在 Java。
7. 邀请关系与海报模板数据（只下发模板和矩形）。
8. 知识库检索与工单 API。
9. 版本、渠道配置、公告、站内信。
10. 推荐问题、八条提示词、多模型权重分流的读写接口；开 run 时把 prompts 与命中的模型注入 Agent。
11. Admin OIDC + PKCE S256（BFF），以及运营接口。页面不在本 feat。

---

## 3. 各 feat 的粘贴稿

### feat 1 — 搭建项目骨架，空跑

**Specify 粘贴稿：**

```text
功能：omino-shop-ai 脚手架（001-scaffold）。

做：
- deploy/docker-compose.local.yml + deploy/env/local.env.example：PostgreSQL 16 + pgvector、Redis 7.4。不要 MySQL。
- services/java：Maven parent、Boot 3.4.11、JDK 21；shop-api-common / shop-api-app / shop-api-admin。APP /api/app 端口 8181，Admin /api/admin 端口 8082。Actuator health。Flyway 能跑空迁移或扩展迁移。
- 统一响应信封骨架：code/message/data/traceId/ts。
- services/agent：uv、锁定版本的 FastAPI，GET /health，只监听本机。
- apps/admin-web、apps/h5、apps/www、apps/land：Vite 空壳能 npm run dev。apps/android 能 assembleDebug，apps/ios 工程能打开。包名 com.omino.shopai。
- 目录一次对齐 AGENTS.md §3。README 只复述 §8 已有命令。

不做：登录、对话、联盟、SSO 业务、复制 mall-guide、把 §6 全部表在本 feat 建完。

验收：compose up 后 APP、Admin、Agent 三个 health 为 200；两个 Java 模块能编译。
权威：AGENTS.md §2 §3 §8 §9。
```

### feat 2 — Java 端

**Specify 粘贴稿：**

```text
功能：Java 双产出物的全部 HTTP、表和同步（002-java-api）。一个功能，按 docs/speckit-phases.md feat 2 的 11 个切片顺序做完。页面不在本功能。

做：
- 表结构按 docs/data-model.md。APP 契约按 docs/api-app.md，Admin 契约按 docs/api-admin.md，Tool 契约按 docs/api-internal.md，对话开 run / SSE / 取消 / 积分按 docs/conversation.md。
- 手机号验证码登录与 JWT。创蓝对齐 omino-sso；local 允许 SMS mock。邀请码绑定即 valid，写入 app_user.inviter_id。
- POST /chat/turns 立刻返回 runId；事件进 Redis；GET SSE 泵间隔 ≤100ms；断开不取消生成；cancel 写 cancelled。本功能结束时 Worker 仍可投递假事件，事件名必须真实。
- 商品详情、goods_snapshot（销量、评价数、商品链接；未知不要写成 0）、convert-link。未授权 40301 NEED_AUTH。禁止下发 PID。浏览、收藏、合集。
- 四平台适配器只在 adapter.platform。搜品/详情失败 50010。不做美团。
- 订单同步：order_status 六值，settle_status 三值。已完成且待结算才给下单用户入账一条 wallet_ledger，重复同步不双写。消费者接口不下发媒体佣金。分成比例走 Nacos commission.user-share-rate。
- 积分与钱包分户。支付渠道 wechat_pay / alipay / wallet。禁止积分提现或转回钱包。
- 海报只存模板与二维码矩形。工单带 ticket_log。版本、runtime-config、公告、点对点站内信。
- 推荐问题、agent_prompt 八个固定 key、llm_model 权重分流。密钥 AES-GCM，读接口不回明文。开 run 注入 prompts 与本轮命中的一条模型。
- Admin：omino-sso Authorization Code + PKCE S256，Java 作 BFF。上帝视角签发短时 JWT；prod 默认关。

不做：Vue/H5/iOS 页面、LangGraph 节点、Flutter、自建管理员密码、服务端合成邀请海报、给上级写第二笔分成、复制 mall-guide。

验收：切片 1–11 各有测试或 curl。无 token 为 40100。未授权转链为 40301。完成单入账一次。Admin 未登录打不到业务接口。
权威：AGENTS.md §2.1 §5 §7，docs/data-model.md，docs/api-app.md，docs/api-admin.md，docs/api-internal.md，docs/conversation.md。
```

### feat 3 — 官方联盟 SDK

**Specify 粘贴稿：**

```text
功能：四个平台的官方联盟 SDK（003-union-sdk）。feat 2 只留了适配器壳，有密钥也会返回 50010。本功能改成真实出站。页面和 Agent 图不在本功能。

做：
- 只在 adapter.platform 调用。淘宝、京东、拼多多、唯品会各自接官方 SDK，做搜品、详情、转链、订单游标增量。订单增量沿用 feat 2 的游标，与入账同一事务。未授权转链仍是 40301 NEED_AUTH。上游失败或密钥为空仍是 50010。不做美团，不新增平台码。
- 引入任何官方包之前，先改 AGENTS.md §2，锁定精确坐标和版本，写明用哪一部分。这些 jar 不在 Maven Central。
- 官方 jar 从 mall-guide 的 sdks 目录复制到 services/java/sdks，只复制 jar 与随包 pom，不复制 mall-guide 的源码、封装类和调用代码。五份坐标：com.taobao:taobao-sdk-java:20250925（含 sources jar）、com.jd:open-api-sdk:20250925、com.pdd:pop-sdk:1.10.16、com.vip:vop-sdk:20250214094310、com.vip:osp-sdk:20250214094310。
- 在 services/java/sdks 提供两条命令：一条用 mvn install:install-file 装进本机 Maven 仓库；一条用 mvn deploy:deploy-file 上传到私有仓库。私有仓库的地址和口令不进 Git，从操作者自己的 settings.xml 读取。
- 未知销量、评价数仍为空，不要写成 0。响应和日志不含密钥与 PID。

不做：页面、LangGraph、新平台码、服务端合成海报、复制 mall-guide 的源码与调用代码、大淘客、把私有仓库口令写进仓库。

验收：五个 jar 在 services/java/sdks。一条命令能装进本地 Maven，一条命令能传到私有仓库，仓库里没有口令。四个平台在测试密钥下，搜品、详情、转链、订单增量都能打到官方；没配密钥时仍是 50010，且不发出站请求。抽查响应无 PID、无密钥。重复同步不双写入账。
权威：AGENTS.md §2 §7 §9，docs/api-app.md，docs/api-internal.md，docs/data-model.md。
```

### feat 4 — 后台管理页面

**Specify 粘贴稿：**

```text
功能：管理后台页面（004-admin-web）。只做 apps/admin-web，调用 feat 2 已有的 /api/admin。

做：
- Vue 3 + Vite + TypeScript + Pinia + Vue Router + Element Plus，版本以 AGENTS.md §2.4 为准。
- 登录走 omino-sso OIDC + PKCE S256（BFF：浏览器只打 Admin API）。禁止账号密码页，禁止浏览器持有 access_token。
- 页面、菜单、路由 ID 对齐 docs/admin-ui.md：工作台、订单、用户列表/详情（用户视角）、会话、工单、提现、积分、海报（浏览器 Canvas 预览）、版本、特殊配置、公告、站内信、推荐问题、提示词、模型分流、知识库、上帝视角、审计。
- 模型密钥只写不回显。提示词不能增删 key。推荐问题上架最多 12 条。订单表能看到媒体返佣与用户分成。

不做：新的后端接口、消费者 H5、官网、落地页。

验收：SSO 登录后能打开上述菜单；改一条提示词、一条推荐问题、一个模型权重后，再查接口能看到变化；响应与表格里没有密钥明文和联盟 PID。
权威：docs/admin-ui.md，docs/api-admin.md，AGENTS.md §2.4。
```

### feat 5 — Python Agent

**Specify 粘贴稿：**

```text
功能：Python Agent（005-agent）。Java 的 run/SSE 已在 feat 2。本功能把 Worker 的假事件换成真正的图。

做：
- 一张 StateGraph，节点与边按 docs/agent.md。意图枚举与 SSE 事件名按 docs/conversation.md。Tool 只打 Java /internal/v1，白名单按 docs/api-internal.md。
- shop.search：槽位不足则 clarify；confidence < 0.6 不搜品。结果 ≤6 张商品卡，>6 只发合集卡。数字和商品只来自 Java。
- 提示词与模型用 invoke 注入的 prompts 与 llm，不在仓库里再藏一套会盖过数据库的提示词。同一 run 只用抽中的那一个模型。
- HITL 只用于授权/取消授权。停止生成仍是 Java 的 Redis cancel，禁止用 interrupt() 当停止。
- 覆盖 me.*、auth.*、order.query、earn.invite、kb.faq、ticket.create、chitchat。无物流就说未知。返佣只复述 userCommission。
- Langfuse 做 trace。prod 必须打开。不引入 LangSmith。

不做：联盟 SDK、转链、改积分、改钱包、对 APP 暴露端口、Flutter。

验收：登录用户说「我要买纸巾」得到 intent，卡片字段来自 Java。问订单不编造物流。取消生成得到 cancelled，刷新不断开生成。
权威：docs/agent.md，docs/conversation.md，docs/api-internal.md。
```

### feat 6 — 消费者 H5

**Specify 粘贴稿：**

```text
功能：消费者 H5（006-h5）。apps/h5，与原生共用 docs/app-ui.md 的页面 ID、路由和 SSE 事件。

做：
- Vue 3 + Vite + TypeScript + Pinia + Tailwind，禁止 Element Plus，禁止 Flutter。
- 客户端头 X-Client-Type=h5、X-App-Channel=web。登录先做手机号验证码。
- 对话：空态推荐问题、流式文本、商品卡、合集卡、澄清、授权卡、停止、断线续传。断开 SSE 不发 cancel。
- 详情是唯一「去购买」。未授权提示 NEED_AUTH。销量/评价数为空不显示 0。goodsUrl 不是购买按钮。
- 订单按 orderStatus 筛选，展示用户返佣与结算状态。再次购买进详情，行内不转链。
- 浏览、收藏、合集移除、积分、钱包与提现、邀请海报（浏览器贴码、左右滑）、工单、站内信、设置。风格和字号只存本地。
- 上帝视角：JWT gv=1 时顶栏常驻且不可关；该态禁止注销。prod 默认没有入口。

不做：落地页 apps/land、官网、管理后台、Android/iOS 工程。

验收：浏览器走完「验证码登录 → 推荐问题或输入买纸巾 → 卡或合集 → 详情 → 停止一次 → 刷新仍能看到已生成内容」。订单、邀请、设置页面 ID 与 app-ui.md 一致。
权威：docs/app-ui.md，docs/api-app.md，docs/conversation.md，AGENTS.md §2.4.2。
```

### feat 7 — iOS（Android 同契约）

**Specify 粘贴稿：**

```text
功能：iOS 与 Android 原生客户端（007-ios）。页面与 H5 同一套 docs/app-ui.md，工程分开。

做：
- iOS：Swift + SwiftUI，最低 iOS 17，网络用 URLSession。Android：Kotlin + Jetpack Compose，包名 com.omino.shopai。禁止 Flutter / RN / KMP。
- 对话、详情、合集、订单、我的、邀请海报（端上贴二维码后分享微信/QQ 或存相册）、登录。Demo 登录仍是手机号验证码；微信开放平台 App、Apple、Google 按 AGENTS.md §2.3，引入前先把精确 SDK 版本写进该表。
- 友盟、极光同样先锁定版本再引入。只上报白名单电商包名。
- API_BASE 指向 APP API。SSE 断线续传，停止才 cancel。

不做：商店上架、H5 里再包一层原生、把管理后台放进 App。

验收：模拟器或真机走完与 H5 相同的主路径：登录、出卡、详情去购买门闩、订单列表、邀请海报能滑。Android 与 iOS 行为一致，允许分两个任务先后做完。
权威：AGENTS.md §2.3，docs/app-ui.md，docs/api-app.md。
```

### feat 8 — 官网与落地页

**Specify 粘贴稿：**

```text
功能：官网与邀请落地页（008-www-land）。

做：
- apps/www：品牌与下载入口，端口 5175。纯静态可以。禁止 Element Plus。
- apps/land：/i/:inviteCode 展示邀请码、尝试拉起 APP、失败则商店下载；/download 只做下载。端口 5176。
- 拉起用 VITE_APP_SCHEME。可选的公开预览只读 feat 2 已有接口；没有接口时落地页仍能展示路径里的邀请码。
- 部署配置对齐 deploy/nginx/www.conf 与 land.conf。

不做：消费者 H5（apps/h5）、管理后台、登录、对话、服务端合成海报。

验收：本地打开官网首页；打开 /i/K7M2P9QX 能看见该邀请码，并有打开 APP 与下载两个出口。
权威：AGENTS.md §2.4.1。
```

---

## 4. 对 AI 的硬约束（可再复制）

```text
硬约束：
- 只做当前 feat。切片顺序写在 tasks 里，不要回头再开一个 Spec Kit 功能。
- 先改对应权威文档再改代码。
- 先在 docs/product-input.md 追加一行再办事。
- mall-guide 只读，禁止复制源码、Mapper、表名、包名。
- 管理后台身份 = omino-sso Authorization Code + PKCE S256。禁止自建管理员账号密码表。
- 一套 Java 两个产出物，禁止 app 与 admin 互相 import。/internal/v1 只挂 app 进程。
- 密钥、PID、relationId 只在 Java 环境变量。模型密钥加密存储，日志里不出现明文。
- 主库只有 PostgreSQL+pgvector。
- 意图用点分名。SSE 事件名以 docs/conversation.md 为准。
- 停止生成走 Redis cancel + abort HTTP。禁止 interrupt() 当停止。断开 SSE 禁止停 Worker。
- 积分扣费只在 Java。积分与 wallet 分户。
- 不引入 AGENTS.md 未锁定的依赖版本。
- 「不做」列表里的能力不要用空接口、空表或 TODO 充当完成。
```

---

## 5. 你怎么点下一步

1. 确认 constitution 已不是模板；若仍是，先改完再开 feat 1。
2. 新对话，贴 §0 开场，加上「开始 feat 1」，再执行 §1 的命令序列。
3. 审 spec / plan 时看范围有没有串 feat：feat 2 写页面、feat 4 或 feat 5 去接官方 SDK、feat 5 直连联盟，都是失败。真实出站只在 feat 3。
4. feat 1、feat 2、feat 3 已合并到 main。当前是 feat 4。feat 4 审完并实现前，不要并行开 feat 5。feat 6 等 feat 5。feat 7 等页面契约在 H5 上走过一遍。feat 8 的官网可以在 feat 1 之后就做。
5. 同一功能不要再跑 `/opsx-propose`。

feat 1、feat 2、feat 3 已在 main。**当前 `/speckit-specify` 是 feat 4。**
