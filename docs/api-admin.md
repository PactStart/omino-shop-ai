# Admin HTTP API（`/api/admin`）

> 本文件是管理后台 HTTP 契约的权威正文。浏览器 Cookie 会话；Java 作 RP 对接 omino-sso（Authorization Code + PKCE S256）。禁止自建管理员密码表。信封见 `AGENTS.md` §5。页面见 [`admin-ui.md`](./admin-ui.md)。表见 [`data-model.md`](./data-model.md)。

SSO 登录/回调/登出路径实现前须先写入本文（尚未锁路径）。

## 用户

运营可见该用户在 App 侧的全部业务数据（资料、绑定、偏好、对话、订单、浏览/收藏/合集、钱包、积分、邀请、授权、工单、站内信）。**禁止**下发 JWT 明文、联盟 PID、完整收款账号、平台原始 JSON。列表手机脱敏；详情返回完整手机（客服）。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/users` | 分页。query：`keyword`（昵称）、`phone`、`inviteCode`、`id`、`status` |
| GET | `/api/admin/v1/users/{id}` | 档案：`app_user`（含 `inviterId`，无邀请人为 `null`）+ identities + preference + installedApps + 钱包/积分摘要 + 邀请摘要 + 各平台授权状态（无 PID）+ 计数（会话/订单/工单/收藏） |
| GET | `/api/admin/v1/users/{id}/goods/history` | 浏览分页 |
| GET | `/api/admin/v1/users/{id}/goods/favorites` | 收藏分页 |
| GET | `/api/admin/v1/users/{id}/goods/collections` | 合集列表（只读） |
| GET | `/api/admin/v1/users/{id}/goods/collections/{cid}` | 合集内未移除商品 |

其它域（订单、会话、工单、钱包流水、积分流水/订单、站内信、提现）**复用既有 Admin 列表**，必须带 `userId`（与路径 id 一致）；未带则仍为全站查询。后台用户详情页一律带该参数。

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
| GET/POST/PATCH | `/api/admin/v1/releases`（及 `/{id}`） | 版本/渠道包；`published` 才参与检查更新 |
| GET/POST/PATCH | `/api/admin/v1/runtime-configs`（及 `/{id}`） | 按端/渠道/`version_code` 范围浅合并 |
| GET/POST/PATCH | `/api/admin/v1/announcements`（及 `/{id}`） | `clientTypes[]` `channels[]` 版本范围 |
| POST | `/api/admin/v1/inbox` | 点对点：`userId` `title` `body`；禁止当群发 |
| GET | `/api/admin/v1/inbox` | 可按 userId 查 |
| POST | `/api/admin/v1/god-view/tokens` | body `{ "userId" }`；短时 APP JWT `gv=1` TTL 1h。须 `GOD_VIEW_ENABLED=true`，写 `admin_audit_log`。`prod` 默认 false；未开返回 `40303` |

## 电商订单

只读。金额为字符串两位或 `null`。含 `mediaCommission`（平台付给媒体）、`userCommission`（分给下单用户）、`parentUserId`、`orderStatus`、`settleStatus`、`walletLedgerId`。无联盟原始 JSON，无物流编造。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/orders` | 分页。query：`userId` `platform` `orderStatus` `settleStatus` `page` `size` |
| GET | `/api/admin/v1/orders/{id}` | 单行全字段（运营可见列，见 [`data-model.md`](./data-model.md) §6.8） |

结算只由订单同步写入钱包，本接口不提供「手动结算」。

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

