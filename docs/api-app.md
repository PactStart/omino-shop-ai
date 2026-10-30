# App HTTP API（`/api/app`）

> 本文件是消费者端 HTTP 契约的权威正文（Android / iOS / H5）。对话 SSE 事件名、开 run / 取消 / 积分扣费见 [`conversation.md`](./conversation.md)。Admin 见 [`api-admin.md`](./api-admin.md)。表结构见 [`data-model.md`](./data-model.md)。

约定与信封见 `AGENTS.md` §4–§5。鉴权：`Authorization: Bearer`（登录除外）。

对话开 run、SSE、取消、积分扣费以 [`conversation.md`](./conversation.md) 为准（`POST /api/app/v1/chat/turns`、`GET .../events`、`POST .../cancel`）。

### 4.1.1 `GET /api/app/v1/chat/suggestions` — 新对话推荐问题

须登录。`data` 为数组，仅 `chat_suggestion.status=active`，按 `sort` 升序、`id` 升序。元素：`{ "id", "text" }`。无配置则为 `[]`。客户端在**没有消息的对话页**展示；点击把 `text` 当作普通用户消息调用 `POST /chat/turns`。下架的不返回。

### 4.2 `POST /api/app/v1/goods/detail` — 商品详情

**Request**

```json
{
  "goodsId": "g_jd_100012345678",
  "platform": "jd",
  "platformGoodsId": "100012345678"
}
```


| 字段              | 类型     | 约束                                |
| --------------- | ------ | --------------------------------- |
| goodsId         | string | 与 platform+platformGoodsId 至少一组必填 |
| platform        | string | 枚举见 [`data-model.md`](./data-model.md)：`taobao` `jd` `pdd` `vip` |
| platformGoodsId | string | 平台侧商品 ID                          |


**Response `data`**

```json
{
  "goodsId": "g_jd_100012345678",
  "platform": "jd",
  "platformGoodsId": "100012345678",
  "title": "示例降噪耳机",
  "subtitle": "主动降噪 40h 续航",
  "imageUrls": ["https://cdn.example/1.jpg"],
  "price": "799.00",
  "couponPrice": "749.00",
  "commissionHint": null,
  "shopName": "品牌旗舰店",
  "salesCount": 12000,
  "reviewCount": 340,
  "goodsUrl": "https://item.jd.com/100012345678.html",
  "detailHtml": null,
  "skuSummary": "黑色 / 官方标配",
  "canPromote": true
}
```

逻辑：`platform + platformGoodsId` 先读 Redis（TTL 分钟级）→ 未命中再打平台适配器 → 写入瘦快照 `goods_snapshot`（禁止存平台原始大 JSON）→ 返回统一卡片。点击进详情 **只走本接口**，不经 Agent。成功后 Java 写 `user_event.kind=browse`。

`salesCount` / `reviewCount` 为整数或 `null`（平台未返回就是 `null`，不要写成 `0`）。`goodsUrl` 是平台商品页，可空。去购买不用这个 URL，仍走 §4.3 转链。

### 4.3 `POST /api/app/v1/goods/convert-link` — 转链（授权校验 + deeplink）

详情页「去购买」按钮调用。**禁止**列表/对话卡片批量转链。不实现 `/goods/buy`、`/goods/promotion-link`。

顺序（全部在 Java）：① 该用户该 `platform` 是否已联盟授权，未授权返回 `40301` 且 `error.reason=NEED_AUTH`（APP 可跳对话发 `auth.grant` 或打开授权页）；② `preferApp=true` 时看已上报 `app_user_installed_app`，未安装则只给 H5/`universalLink`；③ 用服务端 PID/relationId 转链；④ 写入 `click_event`（归因）。**禁止**把联盟 PID 下发客户端。

**Request**

```json
{
  "goodsId": "g_jd_100012345678",
  "platform": "jd",
  "platformGoodsId": "100012345678",
  "preferApp": true
}
```


| 字段                                   | 类型      | 约束                                  |
| ------------------------------------ | ------- | ----------------------------------- |
| goodsId / platform / platformGoodsId | 同 4.2   | 定位商品                                |
| preferApp                            | boolean | 默认 `true`：优先返回可唤起已安装 APP 的 deeplink |


**Response `data`**

```json
{
  "goodsId": "g_jd_100012345678",
  "platform": "jd",
  "clickUrl": "https://union-click.example/xxxx",
  "deeplink": "openapp.jdmobile://virtual?params=...",
  "universalLink": "https://u.jd.com/xxxx",
  "fallbackHttpUrl": "https://item.jd.com/100012345678.html",
  "expiresAt": "2026-10-27T12:00:00+08:00"
}
```

APP 打开顺序：已安装则 `deeplink` → `universalLink` → `fallbackHttpUrl`。

### 4.6 积分套餐与支付（App）

支付密钥与回调验签只在 Java。`credit_order` **不是** `app_order`。渠道枚举：`wechat_pay` | `alipay` | `wallet`。SDK 按 `AGENTS.md` §7.1.6 检索后写入 §2 再引入。Demo 不做 Apple IAP。


| 方法   | 路径                               | 说明                                                                          |
| ---- | -------------------------------- | --------------------------------------------------------------------------- |
| GET  | `/api/app/v1/credits`            | 当前用户 `available`（字符串两位）、`tokensPerCredit`                                   |
| GET  | `/api/app/v1/credits/ledger`     | 积分流水分页；`amount`/`balanceAfter` 为字符串两位                                       |
| GET  | `/api/app/v1/credit-packages`    | 在售套餐（`active`）                                                              |
| POST | `/api/app/v1/credit-orders`      | body：`packageId`、`channel`（`wechat_pay`/`alipay`/`wallet`）、`clientOrderId`。`wallet` 当时扣返佣余额并入账积分 |
| GET  | `/api/app/v1/credit-orders/{id}` | 本人订单状态                                                                      |
| POST | `/api/app/v1/pay/wechat/notify`  | 微信支付回调，验签；成功则入账。无 JWT                                                       |
| POST | `/api/app/v1/pay/alipay/notify`  | 支付宝回调，同上                                                                    |


支付成功同一事务：`credit_order.status=paid`，`credit_ledger.type=purchase`，增加积分。`wallet` 渠道同时写 `wallet_ledger.type=credit_buy`。失败/关单不得加积分。Demo 不做 Apple IAP。

### 4.6.1 登录（Demo：手机号验证码）

短信走创蓝（字段对齐 omino-sso）。`local` 允许 `SMS_PROVIDER=mock`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/app/v1/auth/sms/send` | body：`phone`；限流。无 JWT |
| POST | `/api/app/v1/auth/sms/login` | body：`phone`、`code`、可选 `inviteCode`；签发 APP JWT。绑定邀请则下级立即 `valid` |

微信/Apple/Google 登录 F16 再锁路径。微信须为开放平台移动应用。

### 4.6.2 浏览 / 收藏 / 合集

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/app/v1/goods/history` | 浏览分页，来自 `user_event.kind=browse` |
| GET | `/api/app/v1/goods/favorites` | 收藏分页 |
| POST | `/api/app/v1/goods/favorites` | body：`goodsId`；幂等 |
| DELETE | `/api/app/v1/goods/favorites/{goodsId}` | 取消收藏，幂等 |
| GET | `/api/app/v1/goods/collections/{id}` | 本人合集：`title` + 未移除的 `GoodsCard[]` |
| DELETE | `/api/app/v1/goods/collections/{id}/items/{goodsId}` | 移除（写 `removed_at`）；非本人 `40300` |

合集由搜品 Tool 在结果 **> 6** 时创建，不提供用户「新建合集」。

### 4.7 邀请码、落地链接与海报模板

邀请码在创建 `app_user` 时生成，**之后禁止修改**。字母表 `23456789ABCDEFGHJKLMNPQRSTUVWXYZ`（8 位），CSPRNG，UNIQUE 冲突则重试。禁止用用户 id / 手机号当码。

邀请链接（二维码内容，唯一）：`{INVITE_LAND_BASE_URL}/i/{inviteCode}`，例如 `https://land.example.com/i/K7M2P9QX`。码已在路径里，不要再拼 query。落地页负责：展示码、尝试 Universal Link / App Link / URL Scheme 打开 APP（把码交给客户端）、失败则商店下载。

绑定：新用户首次登录可带 `inviteCode`。解析到已存在用户且非本人则同一事务插入 `app_invite` 并写 `app_user.inviter_id`（一人一个上级，已有则忽略）。禁止自己邀自己。**当前规则：绑定即有效**（`status=valid`，`valid_at=now()`）；以后可能改为首次下单，届时改本文与判定代码。`inviter_id` 写入后不可改。

**海报（客户端合成）**：Java 只下发**当前生效**的模板原图 URL 与二维码矩形。Android / iOS / H5 把 `inviteUrl` 编成二维码，等比居中贴进矩形（保留静区），多模板 **左右滑动切换**。分享微信/QQ/系统相册的是端上产出的图。**禁止** `POST .../render`、禁止 `invite_poster_render`、禁止把 PID/手机号画进码。二维码库引入前按 `AGENTS.md` §7.1.6 锁定。

生效：`status=active` 且当前时间在 `starts_at`–`ends_at`（空=不限）。按 `sort` 升序。

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/app/v1/invite` | APP JWT | 本人 `inviteCode`、`inviteUrl`、邀请统计摘要（人数/有效/下级贡献，周期默认本月） |
| GET | `/api/app/v1/invite/posters` | APP JWT | 生效中模板：`id,name,imageUrl,canvasWidth,canvasHeight,qrX,qrY,qrWidth,qrHeight,sort`（`imageUrl` 为模板原图） |
| GET | `/api/app/v1/invite/lookup` | 无（落地页，须限流） | query `code`；只回 `{ "valid": true }` 或 `40400`。禁止回邀请人昵称/手机 |

登录/注册 body 可含可选 `inviteCode`（路径待补时先改本文）。
### 4.8 客户端身份头、版本更新、特殊配置、公告

所有 `/api/app/v1/**`（登录与 health、支付回调除外）请求头：

| 头 | 约束 |
| --- | --- |
| `X-Client-Type` | 必填：`android` \| `ios` \| `h5` |
| `X-App-Channel` | 必填，≤32。Android 为渠道包名（如 `official` `huawei` `xiaomi` `honor` `oppo` `vivo` `yingyongbao`）；iOS 固定 `appstore`；H5 固定 `web`。白名单在 Nacos `app.channels`，默认含上列 |
| `X-App-Version-Code` | 必填，`yyyyMMdd` 八位整数（如 `20261029`）；只比较这个，不解析 `versionName` |
| `X-App-Version-Name` | 必填，展示用 |

缺头 → `40001`。Android **必须**按渠道打不同包并在运行时读出 channel，禁止一套 APK 写死 `official` 却叫渠道包。

**检查更新** `GET /api/app/v1/app/check-update`（可无 JWT，头仍必填）

在 `app_release` 中取 `status=published` 且 `client_type` 匹配、`channel` 精确匹配（无则回落该端的 `official`/`appstore`/`web`）的 **最大 `version_code`**（日期整数，故晚一天的包更大）。同端同渠道同一天只有一条记录。

| 比较 | `data.action` |
| --- | --- |
| 无更高版本 | `none` |
| 有更高且 `force_update=true` | `force`（客户端阻断，仅更新） |
| 有更高且非强制 | `optional` |

`data` 另含 `versionCode` `versionName` `title` `changelog` `downloadUrl`（APK 或商店链）。H5 的 `downloadUrl` 可空（刷新即可）。

**运行时特殊配置** `GET /api/app/v1/app/runtime-config`（JWT）

匹配 `app_runtime_config`：`status=active` 且 `client_type` 匹配（或 `*`）、`channel` 匹配（或 `*`）、`version_code_min/max` 含当前（空=不限）。多条按 `priority` 降序 **浅合并** `payload`（后者覆盖同名键）。未知键客户端忽略。已锁键：

| payload 键 | 类型 | 含义 |
| --- | --- | --- |
| `hideInvite` | bool | 隐藏邀请入口 |
| `disablePlatforms` | string[] | 搜品/转链禁用的平台码 |
| `preferH5Convert` | bool | 去购买不走 deeplink，只给 H5 |

**公告** `GET /api/app/v1/announcements`（JWT）

`announcement`：`published`、当前时间在 `starts_at`–`ends_at`（空=不限）、`client_types` 含当前端（空=全端）、`channels` 含当前渠道（空=全渠道）、版本范围含当前。按 `sort` 降序。已读由客户端本地记 id，不落库。

**协议** `GET /api/app/v1/legal`：`{ "agreementUrl", "privacyUrl" }`，来自 yml，无 JWT。

### 4.9 站内信、账号设置、注销

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/app/v1/inbox` | 当前用户私信分页；`title` `body` `readAt` `createdAt` |
| GET | `/api/app/v1/inbox/unread-count` | `{ "count": n }` 供「我的」红点 |
| POST | `/api/app/v1/inbox/{id}/read` | 标已读，幂等 |
| PATCH | `/api/app/v1/me/push` | body `{ "enabled": true\|false }` 写 `app_user.push_enabled` |
| POST | `/api/app/v1/me/delete` | 注销：软删 `deleted_at`、作废 refresh；body `{ "confirm": "DELETE" }` |
| GET | `/api/app/v1/me` | 资料；含 `pushEnabled`。风格/字号 **只存端上**，不进本接口 |

风格切换（V1–V8）、字体 `sm\|md\|lg` 仅本地。用户协议/隐私政策打开 `legal` 返回的 URL（系统浏览器或内置 WebView，禁止把正文写死在客户端）。

### 4.10 订单列表（App）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/app/v1/orders` | 本人分页。query：`platform`（`taobao`/`jd`/`pdd`/`vip`，可空=全部）、`orderStatus`（`pending_pay`/`pending_ship`/`shipped`/`completed`/`cancelled`/`unknown`，可空=全部）、`settleStatus`（`none`/`pending`/`settled`，可空=全部）、`page` `size` |
| GET | `/api/app/v1/orders/{id}` | 详情；无物流 `logistics=null` |

列表与详情给消费者的字段：`id` `platform` `goodsId` `goodsTitle` `goodsImageUrl` `payAmount` `userCommission` `orderStatus` `settleStatus` `orderedAt` `shippedAt`。金额为字符串两位或 `null`。**不下发** `mediaCommission`、`parentUserId`、`walletLedgerId`。`userCommission` 在待结算时是当前分成预估，已结算后是入账金额。

### 4.11 返佣钱包与提现

金额为字符串、两位小数。`channel` 只能是 `alipay`。列表和详情只返回 `accountMask`，不返回完整收款账号。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/app/v1/wallet` | 本人 `availableAmount`、`frozenAmount`、`totalEarned` |
| POST | `/api/app/v1/withdrawals` | body：`amount`、`payeeAccount`。`amount` 必须大于 0 且不超过可用余额，否则 `40001`。成功后状态 `pending`，可用余额减少，冻结额增加。不写 `wallet_ledger` |
| GET | `/api/app/v1/withdrawals` | 本人分页 |
| GET | `/api/app/v1/withdrawals/{id}` | 本人一笔。别人的返回 `40400` |

无 JWT：HTTP `401`，`code` 为 `40100`。积分不能走这些路径。

---

列表行若有 `goodsId`，客户端展示「再次购买」，进入商品详情（`POST /goods/detail`）。**禁止**在订单行直接 `convert-link`。无 `goodsId` 则不显示该按钮。

---
