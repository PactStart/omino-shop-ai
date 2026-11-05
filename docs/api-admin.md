# Admin HTTP API（`/api/admin`）

> 本文件是管理后台 HTTP 契约的权威正文。浏览器 Cookie 会话；Java 作 RP 对接 omino-sso（Authorization Code + PKCE S256）。禁止自建管理员密码表。信封见 `AGENTS.md` §5。页面见 [`admin-ui.md`](./admin-ui.md)。表见 [`data-model.md`](./data-model.md)。

## 会话

浏览器只访问 Admin 进程。授权码在 Java 内交换。响应与日志不出现 access_token、refresh_token、id_token。Cookie 名 `sid_admin`，`HttpOnly`、`SameSite=Lax`，生产 `Secure`。不要用 `sid`。令牌只留在服务端会话。`SSO_REDIRECT_URI` 必须包含上下文路径，例如 `http://127.0.0.1:8182/api/admin/v1/auth/callback`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/auth/login` | 无会话时 302 到 omino-sso 授权端点。PKCE 仅 S256。已有会话则 302 到登录后的后台入口 |
| GET | `/api/admin/v1/auth/callback` | 校验 state，用授权码换令牌，建立 `sid_admin`。成功后 302 到管理前端源的 `/`。失败不发会话 cookie。响应和地址栏不出现 access_token |
| POST | `/api/admin/v1/auth/logout` | 让 `sid_admin` 立刻过期（Path 为上下文路径），并作废本应用服务端会话。业务接口只认会话里的登录主体；只有 Cookie、没有登录主体时返回 401。不调用 IdP `/logout`，不结束统一身份会话。随后 302 到 `SSO_LOGIN_URL` 所在源的 `/account/apps`。门户未登录时由其自己进入登录页。未配置 `SSO_LOGIN_URL` 时不跳到 IdP |

未登录访问 `/api/admin/v1/**` 业务路径：HTTP `401`，信封 `code` 为 `40100`，`data` 为 null。已登录但没有该接口要求的权限点：HTTP `403`，信封 `code` 为 `40300`。角色和权限点见 [`admin-access.md`](./admin-access.md)。`GET /api/admin/actuator/health` 不套信封，不要求会话。不建管理员密码表。

## 工作台

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/dashboard` | `syncFailures`、`ticketCount`、`pendingWithdrawals`。`syncFailures` 只计 `taobao` `jd` `pdd` `vip` 里 `last_error` 非空的平台数。`ticketCount` 是工单行数。`pendingWithdrawals` 是 `status=pending` 的提现数。没有行时三个整数都是 0 |

运营指标的路径、字段和口径只写在 [`ops-metrics.md`](./ops-metrics.md)。`GET /api/admin/v1/dashboard` 仍只有上面三个待办，不并入那些指标。

## 用户

运营可见该用户在 App 侧的全部业务数据（资料、绑定、偏好、对话、订单、浏览/收藏/合集、钱包、积分、邀请、授权、工单、站内信）。**禁止**下发 JWT 明文、联盟 PID、完整收款账号、平台原始 JSON。列表手机脱敏；详情返回完整手机（客服）。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/users` | 分页。query：`keyword`（昵称）、`phone`、`inviteCode`、`id`、`status` |
| GET | `/api/admin/v1/users/{id}` | 档案：`app_user`（含 `inviterId`，无邀请人为 `null`）+ identities + preference + installedApps + 钱包/积分摘要 + 邀请摘要 + 各平台授权状态（无 PID）+ 计数（会话/订单/工单/收藏） |
| GET | `/api/admin/v1/users/{id}/goods/history` | 浏览分页。每行 `goodsId` `title` `imageUrl` `createdAt`。标题优先用浏览记录，没有则用商品快照。图来自快照，没有则为空。不下发 `payload` |
| GET | `/api/admin/v1/users/{id}/goods/favorites` | 收藏分页。每行 `goodsId` `title` `imageUrl` `createdAt`。名称和图来自商品快照，没有快照时名称为空 |
| GET | `/api/admin/v1/users/{id}/goods/collections` | 合集列表（只读）。每行含 `id` `title` `createdAt` |
| GET | `/api/admin/v1/users/{id}/goods/collections/{cid}` | 合集内未移除商品。每行 `goodsId` `title` `imageUrl` `sort` `createdAt`。名称和图来自商品快照 |
| GET | `/api/admin/v1/users/{id}/invites` | 该用户邀请的人，分页。每行 `inviteeId` `nickname` `phone`（脱敏）`status` `validAt` `createdAt`。按邀请时间倒序。用户不存在 `40400` |

其它域（订单、会话、工单、钱包流水、积分流水/订单、站内信、提现）**复用既有 Admin 列表**，必须带 `userId`（与路径 id 一致）；未带则仍为全站查询。后台用户详情页一律带该参数。

## 用户对话

只读。不提供修改用户话的方法。响应不含模型密钥和联盟 PID。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/conversations` | 分页。query：`userId` `page` `size`。行含 `id` `userId` `title` `lastIntent` `updatedAt` |
| GET | `/api/admin/v1/conversations/{id}/messages` | 按时间升序。含 `role` `content` `intent` `createdAt` |

## 工单

状态只用 `open` `processing` `closed`。只回复不改状态。`status=closed` 才是关单。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/tickets` | 分页。query：`userId` `status` `page` `size` |
| GET | `/api/admin/v1/tickets/{id}` | 工单当前值加处理记录时间线 |
| POST | `/api/admin/v1/tickets/{id}/reply` | body：`content`。只追加 `kind=reply`。状态不变。已关闭则 `40001` |
| PATCH | `/api/admin/v1/tickets/{id}` | 可选 `status` `priority` `assigneeSsoSub`。改了哪一项就在同一事务追加对应的处理记录 |

## 知识库

`status` 只用 `published` 或 `retired`。新建为 `published`。没有删除。`retired` 不再被知识库检索返回。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/kb/articles` | 含 `retired` |
| POST | `/api/admin/v1/kb/articles` | body：`title` `body`。新建为 `published` |
| PATCH | `/api/admin/v1/kb/articles/{id}` | 可改标题、正文、`status`（`published` 或 `retired`） |

## 审计

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/audit-logs` | 分页，时间倒序。query：`page` `size`。行含 `ssoSub` `action` `resource` `createdAt`。`detail` 若有，也不含密钥、PID、完整收款账号 |

## 钱包流水

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/wallet-ledger` | 分页。query：`userId` `page` `size`。用户详情必须带 `userId`。金额为两位小数字符串。不提供修改历史行的方法 |

## 订单同步

固定四个平台：`taobao` `jd` `pdd` `vip`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/order-sync` | 固定四行。每行 `lastSyncCreateTime` `lastSyncUpdateTime` `lastError` `updatedAt` |
| PATCH | `/api/admin/v1/order-sync/{platform}` | 设置该平台的下单水位和更新水位。body：`lastSyncCreateTime`、`lastSyncUpdateTime`，都可空。空或空串表示清掉这一列。时间必须是带时区的 ISO-8601，否则 `40001`。不改 `lastError`。没有这一行时插入。写入后记审计 `action=order.sync.cursor`，明细只有这两列的新值。平台不是这四个则 `40001` |
| POST | `/api/admin/v1/order-sync/{platform}/retry` | 平台不是这四个则 `40001`，且不调用 APP。先写审计 `action=order.sync.retry`，再触发 APP。APP 受理后立刻返回，拉单在 APP 内异步把两列水位追平到本次请求时间。这个接口返回不代表水位已经追平。追赶失败时 `lastError` 是这次失败的原因，最长 512 字，不含密钥 |

APP 侧触发路径只给这次重试用，**不**进入 [`api-internal.md`](./api-internal.md) 的工具白名单：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/internal/v1/order-sync/{platform}` | 仅内部令牌。受理后立刻返回。APP 异步按平台允许的时间跨度循环拉单，直到两列水位到达本次请求时间。同一平台已在追赶时不再并行一轮 |

## 积分套餐

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET/POST/PATCH | `/api/admin/v1/credit-packages`（及 `/{id}`） | 套餐；下架 `retired`，禁止物理删已有订单引用的套餐。用户侧支付渠道：微信 / 支付宝 / 返佣余额 |
| GET | `/api/admin/v1/credit-rate` | 只读当前 `credit.tokens-per-credit`（Nacos）；不要 PATCH |
| GET | `/api/admin/v1/credit-orders` | 积分支付订单 |
| GET | `/api/admin/v1/credits/ledger` | 全站流水（可按 userId） |
| POST | `/api/admin/v1/credits/adjust` | 调账：`credit_ledger.type=adjust` + `admin_audit_log` |

## 邀请海报模板

Java **只存模板原图与二维码矩形、生效时间**。禁止服务端合成成品、禁止 `invite_poster_render`、禁止 preview 接口把 PNG 存在服务端。运营预览在 Admin 浏览器 Canvas 完成（与 H5 同一算法）。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET/POST/PATCH | `/api/admin/v1/invite-posters`（及 `/{id}`） | CRUD。POST：图（或 `imageObjectKey`）+ `qrX,qrY,qrWidth,qrHeight` + 画布宽高 + 可选 `startsAt`/`endsAt`。下架 `retired` |

`startsAt`/`endsAt` 空 = 不限。两者都有时须 `endsAt > startsAt`。已有用户端缓存的是模板 URL，改图或矩形后 `updated_at` 变化，客户端下次拉列表重贴码。

## 版本 / 配置 / 公告 / 站内信 / 上帝视角

`version_code` 为 `yyyyMMdd` 八位整数（如 `20261029`），同端同渠道唯一。当天再发：PATCH 同一行，禁止第二条同号。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET/POST/PATCH | `/api/admin/v1/releases`（及 `/{id}`） | 版本/渠道包；`published` 才参与检查更新。`changelog` 配 `format`（`plain` / `html`），`html` 按数据模型的白名单清洗后入库 |
| GET/POST/PATCH | `/api/admin/v1/runtime-configs`（及 `/{id}`） | 按端/渠道/`version_code` 范围浅合并 |
| GET/POST/PATCH | `/api/admin/v1/announcements`（及 `/{id}`） | `clientTypes[]` `channels[]` 版本范围。`body` 配 `format`（`plain` / `html`），`html` 按数据模型的白名单清洗后入库 |
| POST | `/api/admin/v1/inbox` | 点对点：`userId` `title` `body` `format`；禁止当群发。`format` 缺省为 `plain`，`html` 按数据模型的白名单清洗后入库 |
| GET | `/api/admin/v1/inbox` | 可按 userId 查 |
| POST | `/api/admin/v1/god-view/tokens` | body `{ "userId" }`；短时 APP JWT `gv=1` TTL 1h。须 `GOD_VIEW_ENABLED=true`，写 `admin_audit_log`。`prod` 默认 false；未开返回 `40303` |

## 电商订单

只读。金额为字符串两位或 `null`。含 `mediaCommission`（平台付给媒体）、`userCommission`（分给下单用户）、`parentUserId`、`orderStatus`、`settleStatus`、`walletLedgerId`。无联盟原始 JSON，无物流编造。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/orders` | 分页。query：`userId` `platform` `platformOrderId` `orderStatus` `settleStatus` `page` `size`。`platformOrderId` 精确匹配，空白则不筛。行含 `goodsTitle` 与 `goodsImageUrl`（同步时的主图，没有则为空） |
| GET | `/api/admin/v1/orders/{id}` | 单行全字段（运营可见列，见 [`data-model.md`](./data-model.md) §6.8） |

结算只由订单同步写入钱包，本接口不提供「手动结算」。

## 返佣提现

金额为字符串、两位小数。列表只返回 `accountMask`。`channel` 只能是 `alipay`。未登录按会话一节返回 HTTP `401`、`code` `40100`。打款失败不是 `50010`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/withdrawals` | 分页。query：`userId`、`status`、`page`、`size` |
| POST | `/api/admin/v1/withdrawals/{id}/payout` | 仅 `pending` 会打款。成功则 `paid` 并写一条 `wallet_ledger.type=withdraw`。失败则仍是 `pending`，冻结额不动，响应 `code` 仍为 `0`，`message` 为「打款未成功，冻结额未动」，`data.payoutResult` 为 `failed`。已 `paid` 的再次调用不再打款 |
| POST | `/api/admin/v1/withdrawals/{id}/reject` | body：`reason`。仅 `pending` 会解冻并变为 `rejected`。已拒绝再调用保持 `rejected` |

## 新对话推荐问题

表 `chat_suggestion`。同时 `active` 最多 12 条。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/chat-suggestions` | 全部，含 `retired`。`id` `text` `sort` `status` `updatedAt` |
| POST | `/api/admin/v1/chat-suggestions` | body：`text` `sort` `status`（默认 `active`）。上架后超过 12 条 `40001` |
| PATCH | `/api/admin/v1/chat-suggestions/{id}` | 改文案、排序、上下架。重新上架同样受 12 条限制 |

不提供物理删除；下架用 `status=retired`。

## Agent 提示词

表 `agent_prompt`。`key` 闭集，见数据模型 §6.29。没有 POST、没有 DELETE。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/agent-prompts` | 八行全量。`key` `name` `content` `updatedAt` `updatedBy` |
| PATCH | `/api/admin/v1/agent-prompts/{key}` | body：`content`（去空白后非空）。写 `updated_by` 与 `admin_audit_log`（`action=agent_prompt.update`，`detail` 只记 key，不记全文） |

未知 `key` 返回 `40400`。下一次开 run 即使用新正文，不经 Nacos。

## 大模型分流

表 `llm_model`。协议固定 OpenAI 兼容。响应**永不**包含 `apiKey` 明文，只含 `apiKeySet`（bool）与 `apiKeyMask`（末 4 位，不足则全掩码）。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/llm-models` | 列表：`id` `name` `baseUrl` `modelName` `weight` `enabled` `timeoutMs` `apiKeySet` `apiKeyMask` `updatedAt` |
| POST | `/api/admin/v1/llm-models` | body：`name` `baseUrl` `modelName` `apiKey` `weight` `enabled` `timeoutMs`。`apiKey` 必填。`weight`≥0，`timeoutMs` 1000–120000。`name` 重复返回 `40001`（`error.field=name`） |
| PATCH | `/api/admin/v1/llm-models/{id}` | 同上字段均可选。不传 `apiKey` = 不改密钥。写审计，`detail` 不含密钥 |

停用：`enabled=false` 或 `weight=0`，不提供物理删除。分流规则见数据模型 §6.30。

