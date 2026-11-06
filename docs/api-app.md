# App HTTP API（`/api/app`）

> 本文件是消费者端 HTTP 契约的权威正文（Android / iOS / H5）。对话 SSE 事件名、开 run / 取消 / 积分扣费见 [`conversation.md`](./conversation.md)。Admin 见 [`api-admin.md`](./api-admin.md)。表结构见 [`data-model.md`](./data-model.md)。接口目录见 [`app-api-catalog.md`](./app-api-catalog.md)，那份不另写字段。

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

顺序（全部在 Java）：① 淘宝、拼多多、唯品会须已联盟授权，未授权返回 `40301` 且 `error.reason=NEED_AUTH`（APP 打开 §4.12 授权页）。京东不需要用户授权，跳过这一步；② 读取该用户的推广身份 `platform_authorization.relation_id`（拼多多转链的 pid 读京东那条），先 Redis 后数据库，没有则同样 `40301`，不出站；③ `preferApp=true` 时看已上报 `app_user_installed_app`，未安装则只给 H5/`universalLink`；④ 用该身份转链；⑤ 写入 `click_event`（归因）。**禁止**把联盟 PID 下发客户端。

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

### 4.6.3 商城搜索、口令与推荐

须登录。搜品、口令、解析、相似、猜你喜欢都要当前用户的推广身份，没有则 `40301` `NEED_AUTH`，不出站。联想词和热搜词不走联盟，走大淘客；密钥未配置时 `data.list` 为 `[]`，不出站。上游失败是 `50010`。分页 `page` 从 1 起，`size` 默认 20、最大 20。`sort` 只接受 `sales` `price` `commission` `coupon`，空则用平台默认排序。

搜索还可以带 `priceMin`、`priceMax`（单位元，最多两位小数；最低价高于最高价是 `40001`）、`brand`、`coupon`。`brand` 和 `coupon` 只有 `true` 才筛选，`false` 或不传表示不限。平台没有对应条件时忽略该字段，不报错：四个平台都认价格和有券；只有拼多多认品牌。`nextId` 是上一页返回的翻页游标，第一页不传。京东把它当作 `searchPosition`，拼多多把它当作 `listId`，淘宝和唯品会忽略。响应仍是商品卡分页；平台给出下一页游标时，`data` 多一个 `nextId`，没有则不出现。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/app/v1/goods/search` | body：`platform` `keyword` `page?` `size?` `sort?` `priceMin?` `priceMax?` `brand?` `coupon?` `nextId?`。`data` 为商品卡分页，可带 `nextId` |
| GET | `/api/app/v1/goods/suggestions` | query：`keyword`。`data.list` 为补全词 |
| GET | `/api/app/v1/goods/hot-words` | `data.list` 为热搜词 |
| POST | `/api/app/v1/goods/command` | body：`goodsId`，或 `platform` + `platformGoodsId`。`data.command` 是口令。该平台不支持时 `40001` |
| POST | `/api/app/v1/goods/parse` | body：`platform` `content`。只支持 `taobao` `vip`，其它平台 `40001`。`data.goods` 能定位商品时是商品卡，否则 `null`；`data.url` 是解析出的链接，可空 |
| POST | `/api/app/v1/goods/similar` | body：`platform` `platformGoodsId` 或 `goodsId`，以及 `page` `size`。不支持的平台 `40001`。`data` 为商品卡分页 |
| POST | `/api/app/v1/goods/guess` | body：`platform` `page` `size`。不支持的平台 `40001`。`data` 为商品卡分页 |

商品卡字段与对话卡相同。口令、解析链接和日志里都不放推广位。

### 4.6.4 商城首页

须登录。`GET /api/app/v1/mall`。按 `X-Client-Type`、`X-App-Channel` 过滤。只下发 `published`、且当前时间落在生效窗内的条目。`clientTypes` 或 `channels` 为空数组表示不限制。

`data.banner`：`intervalMs`（缺省 3000，`0` 不自动播）、`items[]`。每项 `id` `title` `imageUrl` `actionType` `action`。`action` 是对象，字段见 [`data-model.md`](./data-model.md) 商城布局。`items` 按 `sort` 升序。

`data.nav`：`columns`（4 到 6，缺省 5）、`categories[]`。分类含 `id` `name` `items[]`，条目字段与 banner 相同。分类和图标都按 `sort` 升序。没有可展示图标的分类不下发。

搜索条和猜你喜欢不在这个接口里。猜你喜欢用 `POST /api/app/v1/goods/guess`。

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

`data` 另含 `versionCode` `versionName` `title` `changelog` `format` `downloadUrl`（APK 或商店链）。`format` 为 `plain` 或 `html`，含义见 [`data-model.md`](./data-model.md) 站内信一节。H5 的 `downloadUrl` 可空（刷新即可）。

**运行时特殊配置** `GET /api/app/v1/app/runtime-config`（JWT）

匹配 `app_runtime_config`：`status=active` 且 `client_type` 匹配（或 `*`）、`channel` 匹配（或 `*`）、`version_code_min/max` 含当前（空=不限）。多条按 `priority` 降序 **浅合并** `payload`（后者覆盖同名键）。未知键客户端忽略。已锁键：

| payload 键 | 类型 | 含义 |
| --- | --- | --- |
| `hideInvite` | bool | 隐藏邀请入口 |
| `disablePlatforms` | string[] | 搜品/转链禁用的平台码 |
| `preferH5Convert` | bool | 去购买不走 deeplink，只给 H5 |

**公告** `GET /api/app/v1/announcements`（JWT）

`announcement`：`published`、当前时间在 `starts_at`–`ends_at`（空=不限）、`client_types` 含当前端（空=全端）、`channels` 含当前渠道（空=全渠道）、版本范围含当前。按 `sort` 降序。已读由客户端本地记 id，不落库。每条含 `body` 与 `format`（`plain` / `html`）。

**协议** `GET /api/app/v1/legal`：`{ "agreementUrl", "privacyUrl" }`，来自 yml，无 JWT。

### 4.9 站内信、账号设置、注销

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/app/v1/inbox` | 当前用户私信分页；`title` `body` `format` `readAt` `createdAt`。`format` 为 `plain` 或 `html` |
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

列表与详情给消费者的字段：`id` `platform` `goodsId` `goodsTitle` `goodsImageUrl` `payAmount` `userCommission` `orderStatus` `settleStatus` `orderedAt` `shippedAt`。金额为字符串两位或 `null`。**不下发** `mediaCommission`、`parentUserId`、`walletLedgerId`、`paidAt`、`completedAt`、`extras`。`userCommission` 在待结算时是当前分成预估，已结算后是入账金额。

### 4.11 返佣钱包与提现

金额为字符串、两位小数。`channel` 只能是 `alipay`。列表和详情只返回 `accountMask`，不返回完整收款账号。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/app/v1/wallet` | 本人 `availableAmount`、`frozenAmount`、`totalEarned` |
| POST | `/api/app/v1/withdrawals` | body：`amount`、`payeeAccount`。`amount` 必须大于 0 且不超过可用余额，否则 `40001`。成功后状态 `pending`，可用余额减少，冻结额增加。不写 `wallet_ledger` |
| GET | `/api/app/v1/withdrawals` | 本人分页 |
| GET | `/api/app/v1/withdrawals/{id}` | 本人一笔。别人的返回 `40400` |

无 JWT：HTTP `401`，`code` 为 `40100`。积分不能走这些路径。

### 4.12 联盟授权

须登录，除淘宝回调。平台只接受 `taobao` `jd` `pdd` `vip`。响应不下发 `relation_id`、openId、pid。

`data`：

```json
{
  "platform": "taobao",
  "required": true,
  "authorized": false,
  "authUrl": "https://oauth.taobao.com/authorize?..."
}
```

| 字段 | 含义 |
| --- | --- |
| required | 京东为 `false`，其它三个为 `true` |
| authorized | 当前能否当作已授权。京东恒为 `true`，且不调用京东 |
| authUrl | 未授权时给客户端打开的一条链接。已授权或不需要授权时为 `null` |

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/app/v1/me/authorizations/status` | body `{ "platform" }`。京东直接返回 `required=false`、`authorized=true`、`authUrl=null`。淘宝看本表：已授权且有 relation_id 则不再给链接；否则返回手机页授权链接，`state` 用 `JWT_ACCESS_SECRET` 签名，30 分钟内有效。拼多多先要该用户的京东推广身份，没有则 `40001`（文案不出现 pid）；再问官方是否备案，未备案则返回一条授权链接。唯品会用本表里的 openId 问官方；还没有就先生成并记下，状态为 `none`，再取备案链接 |
| POST | `/api/app/v1/me/authorizations/revoke` | body `{ "platform" }`。京东返回 `40001`。尚未授权返回 `40001`。淘宝只把本表改为 `revoked` 并清空 relation_id。唯品会先官方解绑，成功后再改本表，openId 保留。拼多多没有官方解绑，只改本表；下次再查状态时若官方仍是已备案，会回到已授权 |
| GET | `/api/app/v1/me/authorizations/taobao/callback` | 淘宝浏览器回调，不校验登录和客户端头。query：`code`、`state`。`state` 无效或过期为 `40001`。用授权码换票并做渠道备案，成功后本表为 `authorized`。同一授权码十分钟内重复打开，沿用第一次的结果，不再向淘宝提交。`error` 非空则为 `40001`，不把官方说明原文返回 |

拼多多或唯品会查询失败、淘宝换票或备案失败，为 `50010`，文案固定「平台查询失败」。邀请码或回调地址没配时，淘宝也是这一码，不出站。

---

列表行若有 `goodsId`，客户端展示「再次购买」，进入商品详情（`POST /goods/detail`）。**禁止**在订单行直接 `convert-link`。无 `goodsId` 则不显示该按钮。

---
