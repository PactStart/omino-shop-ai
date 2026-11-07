# APP API 接口清单

本文件是消费者端 HTTP 的权威正文（Android / iOS / H5）。各节的表是路径清单；同一接口的字段和规则写在对应小节，不另起一份契约。

| 要查什么 | 去哪 |
| --- | --- |
| 开 run、SSE 事件、取消、积分扣费 | [`conversation.md`](./conversation.md) |
| 埋点 body | [`ops-metrics.md`](./ops-metrics.md) §6.3 |
| 页面 | [`ui/README.md`](./ui/README.md) |
| 表结构 | [`data-model.md`](./data-model.md) |

前缀是 `/api/app`。JSON 接口走统一信封，`data` 才是业务体。SSE 不是信封。

除下面写明「不要」的接口外，业务请求都要 `Authorization: Bearer`，以及 `X-Client-Type`、`X-App-Channel`、`X-App-Version-Code`、`X-App-Version-Name`。

「页面」只记下已经点名的页面 ID。`/internal/v1` 和 Admin 不在本清单。约定与信封见 `AGENTS.md` §4–§5。

## 登录

| 方法 | 路径 | 作用 | 登录 | 客户端头 | 页面 |
| --- | --- | --- | --- | --- | --- |
| POST | `/api/app/v1/auth/sms/send` | 发短信验证码。body：`phone` | 不要 | 不要 | `loginPhone` |
| POST | `/api/app/v1/auth/sms/login` | 验证码登录，签发 APP JWT。body：`phone` `code`，可选 `inviteCode` | 不要 | 不要 | `loginPhone` |

微信、Apple、Google 登录还没有路径。微信须为开放平台移动应用。

短信走创蓝（字段对齐 omino-sso）。`local` 允许 `SMS_PROVIDER=mock`。生产禁止 mock。登录成功签发 APP JWT。body 里的 `inviteCode` 若对上别人的码，且本人还没有上级，同一事务写入邀请关系，`inviter_id` 之后不可改。禁止自己邀自己。绑定即 `valid`。

## 对话

| 方法 | 路径 | 作用 | 登录 | 客户端头 | 页面 |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/app/v1/chat/suggestions` | 空对话的推荐问题。`data[]`：`id` `text` | 要 | 要 | `chat` |
| POST | `/api/app/v1/chat/turns` | 开一轮。立刻返回 `conversationId` `runId` `clientMsgId` | 要 | 要 | `chat` |
| GET | `/api/app/v1/chat/runs/{runId}/events` | SSE 拉事件。断线用 `Last-Event-ID` 续传 | 要 | 要 | `chat` |
| POST | `/api/app/v1/chat/runs/{runId}/cancel` | 用户点停止 | 要 | 要 | `chat` |
| POST | `/api/app/v1/files/image` | 上传图片，拿到 `imageObjectKey`，再作为图片消息开 run | 要 | 要 | `chat` |

工单在对话里由 Tool 建单，没有单独的消费者工单列表接口。

推荐问题只返回 `active`，按 `sort`、`id` 升序。无配置则 `data` 为 `[]`。点击把 `text` 当作普通用户消息调用开 run。图片先走上传接口拿到 `imageObjectKey`，再以 `inputType=image` 开 run。

## 商城与商品

| 方法 | 路径 | 作用 | 登录 | 客户端头 | 页面 |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/app/v1/mall` | 首页布局。`data.banner`、`data.nav` | 要 | 要 | `mall` |
| POST | `/api/app/v1/goods/search` | 分页搜品。可带 `nextId` | 要 | 要 | `mallSearch` |
| GET | `/api/app/v1/goods/suggestions` | 关键词补全。`data.list` | 要 | 要 | |
| GET | `/api/app/v1/goods/hot-words` | 热搜词。`data.list` | 要 | 要 | |
| POST | `/api/app/v1/goods/guess` | 猜你喜欢，商品卡分页 | 要 | 要 | `mall` |
| POST | `/api/app/v1/goods/detail` | 商品详情。成功后记浏览 | 要 | 要 | `goodsDetail` |
| POST | `/api/app/v1/goods/convert-link` | 去购买转链。未授权 `40301` | 要 | 要 | `goodsDetail` |
| POST | `/api/app/v1/goods/similar` | 相似商品，商品卡分页 | 要 | 要 | `goodsDetail` |
| POST | `/api/app/v1/goods/command` | 商品口令。`data.command` | 要 | 要 | `goodsDetail` |
| POST | `/api/app/v1/goods/parse` | 解析链接或口令。只支持淘宝、唯品会 | 要 | 要 | |
| GET | `/api/app/v1/goods/history` | 浏览记录分页 | 要 | 要 | `goodsHistory` |
| GET | `/api/app/v1/goods/favorites` | 收藏分页 | 要 | 要 | `goodsFavorites` |
| POST | `/api/app/v1/goods/favorites` | 收藏。body：`goodsId` | 要 | 要 | `goodsDetail` |
| DELETE | `/api/app/v1/goods/favorites/{goodsId}` | 取消收藏 | 要 | 要 | `goodsFavorites` |
| GET | `/api/app/v1/goods/collections/{id}` | 本人合集标题和商品卡 | 要 | 要 | `goodsCollection` |
| DELETE | `/api/app/v1/goods/collections/{id}/items/{goodsId}` | 从合集移除一件 | 要 | 要 | `goodsCollection` |

### 商品详情与转链

详情请求至少给 `goodsId`，或 `platform` + `platformGoodsId`。`platform` 只接受 `taobao` `jd` `pdd` `vip`。

`data`：`goodsId` `platform` `platformGoodsId` `title` `subtitle` `imageUrls` `price` `couponPrice` `commissionHint` `shopName` `salesCount` `reviewCount` `goodsUrl` `detailHtml` `skuSummary` `canPromote`。`salesCount` / `reviewCount` 为整数或 `null`，平台没给就不要写成 `0`。`goodsUrl` 是商品页，可空，不是购买按钮。

逻辑：先读 Redis 短缓存，未命中再打平台适配器，写入瘦快照。点击进详情只走本接口，不经 Agent。成功后写浏览。

转链只给详情页「去购买」。禁止列表或对话卡片批量转链，不实现 `/goods/buy`、`/goods/promotion-link`。顺序全在 Java：淘宝、拼多多、唯品会未授权返回 `40301`，`error.reason=NEED_AUTH`；京东跳过授权。再读该用户的 `relation_id`（拼多多转链的 pid 读京东那条），先 Redis 后数据库，没有则同样 `40301`，不出站。`preferApp` 默认 `true`，为真时看已上报的已装 APP，未安装则只给 H5 / `universalLink`。然后用该身份转链，并写 `click_event`。禁止下发联盟 PID。

转链 `data`：`goodsId` `platform` `clickUrl` `deeplink` `universalLink` `fallbackHttpUrl` `expiresAt`。打开顺序：已安装则 `deeplink` → `universalLink` → `fallbackHttpUrl`。

### 搜索、口令与推荐

搜品、口令、解析、相似、猜你喜欢都要当前用户的推广身份，没有则 `40301`，不出站。联想词和热搜词走大淘客；密钥未配置时 `data.list` 为 `[]`，不出站。上游失败是 `50010`。

分页 `page` 从 1 起，`size` 默认 20、最大 20。`sort` 只接受 `sales` `price` `commission` `coupon`，空则用平台默认排序。`priceMin` / `priceMax` 单位元、最多两位小数，最低价高于最高价是 `40001`。`brand` 和 `coupon` 只有 `true` 才筛选。四个平台都认价格和有券；只有拼多多认品牌。平台没有的条件忽略，不报错。`nextId` 是上一页游标，第一页不传。京东把它当作 `searchPosition`，拼多多当作 `listId`，淘宝和唯品会忽略。有下一页游标时 `data` 多写 `nextId`，没有则不出现。

口令 body 是 `goodsId`，或 `platform` + `platformGoodsId`；不支持的平台 `40001`。解析 body 是 `platform` `content`，只支持淘宝和唯品会。`data.goods` 能定位时是商品卡，否则 `null`；`data.url` 可空。相似可用 `platformGoodsId` 或 `goodsId`。口令、解析链接和日志里都不放推广位。商品卡字段与对话卡相同。

合集由搜品在结果多于 6 条时创建，不提供用户新建。移除只给本人，否则 `40300`。

### 商城首页布局

`GET /mall` 按 `X-Client-Type`、`X-App-Channel` 过滤。只下发 `published`、且当前时间落在生效窗内的条目。`clientTypes` 或 `channels` 为空数组表示不限制。

`data.banner`：`intervalMs`（缺省 3000，`0` 不自动播）、`items[]`。每项 `id` `title` `imageUrl` `actionType` `action`。`action` 是对象，字段见 [`data-model.md`](./data-model.md) 商城布局。`data.nav`：`columns`（4 到 6，缺省 5）、`categories[]`。分类含 `id` `name` `items[]`。没有可展示图标的分类不下发。搜索和猜你喜欢不在这个接口里。

## 订单

| 方法 | 路径 | 作用 | 登录 | 客户端头 | 页面 |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/app/v1/orders` | 本人电商订单分页。可筛平台、订单状态、结算状态 | 要 | 要 | `orders` |
| GET | `/api/app/v1/orders/{id}` | 一笔订单。无物流则 `logistics` 为 `null`。消费者页面不打开 | 要 | 要 | |

query：`platform`、`orderStatus`、`settleStatus` 都可空，空表示全部。平台四码；订单状态 `pending_pay` `pending_ship` `shipped` `completed` `cancelled` `unknown`；结算状态 `none` `pending` `settled`。

列表和详情给消费者的字段：`id` `platform` `goodsId` `goodsTitle` `goodsImageUrl` `payAmount` `userCommission` `orderStatus` `settleStatus` `orderedAt` `shippedAt`。金额为字符串两位或 `null`。不下发 `mediaCommission`、`parentUserId`、`walletLedgerId`、`paidAt`、`completedAt`、`extras`。`userCommission` 在待结算时是当前分成预估，已结算后是入账金额。

有 `goodsId` 时整张订单卡片进入商品详情。禁止在订单行直接转链。无 `goodsId` 时卡片不可点。消费者不打开订单详情页。无物流时 `logistics` 为 `null`。

## 我的

| 方法 | 路径 | 作用 | 登录 | 客户端头 | 页面 |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/app/v1/me` | 资料，含 `pushEnabled`。风格和字号不在这里 | 要 | 要 | `me` `profile` |
| PATCH | `/api/app/v1/me/push` | 推送开关。body：`enabled` | 要 | 要 | `settingsPush` |
| POST | `/api/app/v1/me/delete` | 注销。body：`confirm` 必须是 `DELETE` | 要 | 要 | `settings` |
| GET | `/api/app/v1/credits` | 积分余额和 `tokensPerCredit` | 要 | 要 | `credits` |
| GET | `/api/app/v1/credits/ledger` | 积分流水分页 | 要 | 要 | `creditLedger` |
| GET | `/api/app/v1/credit-packages` | 在售积分套餐 | 要 | 要 | `creditPackages` |
| POST | `/api/app/v1/credit-orders` | 买积分。渠道 `wechat_pay` `alipay` `wallet` | 要 | 要 | `creditPackages` |
| GET | `/api/app/v1/credit-orders/{id}` | 一笔积分支付单 | 要 | 要 | `creditOrder` |
| GET | `/api/app/v1/wallet` | 返佣钱包三指标 | 要 | 要 | `wallet` |
| POST | `/api/app/v1/withdrawals` | 申请提现到支付宝 | 要 | 要 | `withdraw` |
| GET | `/api/app/v1/withdrawals` | 提现分页 | 要 | 要 | `withdrawRecords` |
| GET | `/api/app/v1/withdrawals/{id}` | 一笔提现，只有脱敏账号 | 要 | 要 | `withdrawRecords` |
| GET | `/api/app/v1/invite` | 邀请码、邀请链接、本月统计 | 要 | 要 | `invite` |
| GET | `/api/app/v1/invite/posters` | 生效中的海报模板和二维码矩形 | 要 | 要 | `invite` |
| GET | `/api/app/v1/inbox` | 站内信分页 | 要 | 要 | `inbox` |
| GET | `/api/app/v1/inbox/unread-count` | 未读数，给「我的」红点 | 要 | 要 | `me` |
| POST | `/api/app/v1/inbox/{id}/read` | 标已读 | 要 | 要 | `inbox` |
| POST | `/api/app/v1/me/authorizations/status` | 查一个平台要不要授权、是否已授权、授权链接 | 要 | 要 | `authorizations` |
| POST | `/api/app/v1/me/authorizations/revoke` | 取消一个平台的授权。京东拒绝 | 要 | 要 | `authorizations` |

显示风格和字号只存在端上，没有接口。风格是 V1 晴空绿、V2 科技黑、V3 简约白、V4 咸鱼黄、V5 京东红。字号是 `sm` `md` `lg`。协议打开 `legal` 返回的 URL，禁止把正文写死在客户端。

积分支付密钥和回调验签只在 Java。`credit_order` 不是电商订单。渠道 `wechat_pay` `alipay` `wallet`。Demo 不做 Apple IAP。余额接口的 `available`、`tokensPerCredit` 为字符串两位。流水分页的 `amount`、`balanceAfter` 同样。在售套餐只含 `active`。下单 body：`packageId` `channel` `clientOrderId`。`wallet` 当时扣返佣余额并入账积分。支付成功同一事务把订单标为已支付、写积分流水并增加积分；`wallet` 渠道同时写钱包流水 `credit_buy`。失败或关单不加积分。

返佣金额为字符串、两位小数。提现 `channel` 只能是 `alipay`。列表和详情只返回 `accountMask`。申请 body：`amount` `payeeAccount`。金额必须大于 0 且不超过可用余额，否则 `40001`。成功后 `pending`，可用减少、冻结增加，不写钱包流水。别人的提现单 `40400`。无 JWT 为 HTTP `401`、`code` `40100`。积分不能走提现。

### 邀请码与海报

邀请码在创建用户时生成，之后不可改。字母表 `23456789ABCDEFGHJKLMNPQRSTUVWXYZ`，8 位。禁止用用户 id 或手机号当码。邀请链接是 `{INVITE_LAND_BASE_URL}/i/{inviteCode}`，码在路径里，不再拼 query。

Java 只下发生效中的模板原图和二维码矩形。端上把 `inviteUrl` 编成二维码贴进矩形。禁止服务端合成海报，禁止把 PID 或手机号画进码。模板字段：`id` `name` `imageUrl` `canvasWidth` `canvasHeight` `qrX` `qrY` `qrWidth` `qrHeight` `sort`。邀请接口还回 `inviteCode`、`inviteUrl` 和本月统计（人数、有效、下级贡献）。

落地页 `lookup` 的 query 是 `code`。只回 `{ "valid": true }` 或 `40400`，禁止回邀请人昵称或手机。

### 联盟授权

须登录，淘宝回调除外。平台只接受四个码。响应不下发 `relation_id`、openId、pid。

`data`：`platform` `required` `authorized` `authUrl`。京东 `required=false` 且 `authorized` 恒为 `true`，不调用京东，`authUrl` 为 `null`。其余三个 `required=true`。未授权时 `authUrl` 是一条打开链接。

查状态：淘宝已授权且有 relation_id 则不再给链接，否则返回手机页授权链接，`state` 签名后 30 分钟内有效。拼多多先要该用户的京东推广身份，没有则 `40001`（文案不出现 pid）；再问官方是否备案。唯品会用本表 openId 问官方；还没有就先生成并记下。

取消：京东 `40001`。尚未授权 `40001`。淘宝只把本表改为 `revoked` 并清空 relation_id。唯品会先官方解绑，成功后再改本表，openId 保留。拼多多没有官方解绑，只改本表。

淘宝回调不校验登录和客户端头。query：`code` `state`。`state` 无效或过期为 `40001`。同一授权码十分钟内重复打开，沿用第一次的结果。查询、换票或备案失败为 `50010`，文案固定「平台查询失败」。

站内信分页字段：`title` `body` `format` `readAt` `createdAt`。`format` 为 `plain` 或 `html`。未读数是 `{ "count": n }`。标已读幂等。资料含 `pushEnabled`。注销 body 的 `confirm` 必须是 `DELETE`，软删并作废 refresh。

## 壳、协议与埋点

| 方法 | 路径 | 作用 | 登录 | 客户端头 | 页面 |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/app/v1/app/check-update` | 检查更新。`data.action` 为 `none` `optional` `force` | 可无 | 要 | `settingsAbout` `forceUpdate` |
| GET | `/api/app/v1/app/runtime-config` | 按端、渠道、版本浅合并的特殊配置 | 要 | 要 | |
| GET | `/api/app/v1/announcements` | 当前端可见的公告 | 要 | 要 | |
| GET | `/api/app/v1/legal` | 用户协议和隐私政策 URL | 不要 | 要 | `login` `settingsAbout` |
| POST | `/api/app/v1/telemetry/events` | 客户端行为。`card_impression` `poster_share` | 要 | 要 | |

## 不进 APP 页面

这些接口给支付渠道、淘宝浏览器或落地页，不绑进消费者页面。

| 方法 | 路径 | 作用 | 登录 | 客户端头 |
| --- | --- | --- | --- | --- |
| POST | `/api/app/v1/pay/wechat/notify` | 微信支付回调验签并入账 | 不要 | 不要 |
| POST | `/api/app/v1/pay/alipay/notify` | 支付宝回调验签并入账 | 不要 | 不要 |
| GET | `/api/app/v1/me/authorizations/taobao/callback` | 淘宝授权回调。query：`code` `state` | 不要 | 不要 |
| GET | `/api/app/v1/invite/lookup` | 落地页校验邀请码是否存在。query：`code` | 不要 | 要 |
| POST | `/api/app/v1/telemetry/land` | 落地页曝光和唤起。不要 JWT | 不要 | 要 |

### 客户端头、更新、配置、公告

除登录、健康检查和支付回调外，`/api/app/v1/**` 都要下面四个头。缺头是 `40001`。

| 头 | 约束 |
| --- | --- |
| `X-Client-Type` | `android` \| `ios` \| `h5` |
| `X-App-Channel` | 必填，不超过 32。Android 是渠道包名；iOS 固定 `appstore`；H5 固定 `web`。白名单在 Nacos `app.channels` |
| `X-App-Version-Code` | `yyyyMMdd` 八位整数。只比较这个 |
| `X-App-Version-Name` | 展示用 |

Android 必须按渠道打不同包。禁止一套 APK 写死 `official` 却叫渠道包。

检查更新取已发布、端匹配、渠道精确匹配（没有则回落该端的 `official` / `appstore` / `web`）的最大 `version_code`。`data.action`：没有更高版本是 `none`；有更高且强制是 `force`；有更高且非强制是 `optional`。另含 `versionCode` `versionName` `title` `changelog` `format` `downloadUrl`。H5 的 `downloadUrl` 可空。

运行时配置取生效中、端和渠道匹配（或 `*`）、版本范围含当前的行，按 `priority` 降序浅合并。已锁键：`hideInvite`（bool）、`disablePlatforms`（string[]）、`preferH5Convert`（bool）。未知键客户端忽略。

公告取已发布、时间窗内、端和渠道以及版本范围命中的行，按 `sort` 降序。已读由客户端本地记 id。每条含 `body` 与 `format`。

协议 `data` 是 `agreementUrl` 和 `privacyUrl`，来自环境配置，无 JWT。
