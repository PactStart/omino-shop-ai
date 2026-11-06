# APP API 接口清单

给 Android、iOS、消费者 H5 做界面数据绑定用的目录。路径以这里为准列全；入参、响应字段、错误码不在这里再写一遍。

| 要查什么 | 去哪 |
| --- | --- |
| 字段、枚举、错误码 | [`api-app.md`](./api-app.md) |
| 开 run、SSE 事件、取消、积分扣费 | [`conversation.md`](./conversation.md) |
| 埋点 body | [`ops-metrics.md`](./ops-metrics.md) §6.3 |
| 页面 ID | [`app-ui.md`](./app-ui.md) |

前缀是 `/api/app`。JSON 接口走统一信封，`data` 才是业务体。SSE 不是信封。

除下面写明「不要」的接口外，业务请求都要 `Authorization: Bearer`，以及 `X-Client-Type`、`X-App-Channel`、`X-App-Version-Code`、`X-App-Version-Name`。

「页面」只记下契约里已经点名的页面 ID。绑定表再按页面补组件和字段，不要把字段抄回本文件。`/internal/v1` 和 Admin 不在本清单。

## 登录

| 方法 | 路径 | 作用 | 登录 | 客户端头 | 页面 |
| --- | --- | --- | --- | --- | --- |
| POST | `/api/app/v1/auth/sms/send` | 发短信验证码。body：`phone` | 不要 | 不要 | `loginPhone` |
| POST | `/api/app/v1/auth/sms/login` | 验证码登录，签发 APP JWT。body：`phone` `code`，可选 `inviteCode` | 不要 | 不要 | `loginPhone` |

微信、Apple、Google 登录还没有路径。

## 对话

| 方法 | 路径 | 作用 | 登录 | 客户端头 | 页面 |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/app/v1/chat/suggestions` | 空对话的推荐问题。`data[]`：`id` `text` | 要 | 要 | `chat` |
| POST | `/api/app/v1/chat/turns` | 开一轮。立刻返回 `conversationId` `runId` `clientMsgId` | 要 | 要 | `chat` |
| GET | `/api/app/v1/chat/runs/{runId}/events` | SSE 拉事件。断线用 `Last-Event-ID` 续传 | 要 | 要 | `chat` |
| POST | `/api/app/v1/chat/runs/{runId}/cancel` | 用户点停止 | 要 | 要 | `chat` |
| POST | `/api/app/v1/files/image` | 上传图片，拿到 `imageObjectKey`，再作为图片消息开 run | 要 | 要 | `chat` |

工单在对话里由 Tool 建单，没有单独的消费者工单列表接口。

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
| POST | `/api/app/v1/goods/similar` | 相似商品，商品卡分页 | 要 | 要 | |
| POST | `/api/app/v1/goods/command` | 商品口令。`data.command` | 要 | 要 | |
| POST | `/api/app/v1/goods/parse` | 解析链接或口令。只支持淘宝、唯品会 | 要 | 要 | |
| GET | `/api/app/v1/goods/history` | 浏览记录分页 | 要 | 要 | `goodsHistory` |
| GET | `/api/app/v1/goods/favorites` | 收藏分页 | 要 | 要 | `goodsFavorites` |
| POST | `/api/app/v1/goods/favorites` | 收藏。body：`goodsId` | 要 | 要 | `goodsDetail` |
| DELETE | `/api/app/v1/goods/favorites/{goodsId}` | 取消收藏 | 要 | 要 | `goodsFavorites` |
| GET | `/api/app/v1/goods/collections/{id}` | 本人合集标题和商品卡 | 要 | 要 | `goodsCollection` |
| DELETE | `/api/app/v1/goods/collections/{id}/items/{goodsId}` | 从合集移除一件 | 要 | 要 | `goodsCollection` |

商品卡和详情字段见 [`api-app.md`](./api-app.md) §4.2、§4.6.3。订单行上的再次购买进详情，不在列表里转链。

## 订单

| 方法 | 路径 | 作用 | 登录 | 客户端头 | 页面 |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/app/v1/orders` | 本人电商订单分页。可筛平台、订单状态、结算状态 | 要 | 要 | `orders` |
| GET | `/api/app/v1/orders/{id}` | 一笔订单。无物流则 `logistics` 为 `null` | 要 | 要 | `orderDetail` |

消费者字段见 [`api-app.md`](./api-app.md) §4.10。不下发媒体返佣、上级、钱包流水、付款时间、完成时间、`extras`。

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

显示风格和字号只存在端上，没有接口。

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
