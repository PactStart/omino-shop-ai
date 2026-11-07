# 第 5 层：我的、积分、钱包、邀请与授权

## 生成范围

页面：`me`、`profile`、`credits`、`creditPackages`、`creditLedger`、`creditOrder`、`wallet`、`withdraw`、`withdrawRecords`、`authorizations`、`invite`。

默认 V1。资产相关字段以 [`app-api-catalog.md`](../../app-api-catalog.md) 的「我的」、邀请和联盟授权为准。

## me `/me`

布局：MeHeader → 商品组（浏览/收藏）→ 资产组（积分/钱包/邀请）→ 账号组（资料/授权）→ 支持组（站内信/工单/设置）→ “积分与返佣分户”脚注 → TabBar。

并行加载 `GET /me` 与 inbox unread-count；未读为 0 不显示 Badge。运行时 `hideInvite=true` 时隐藏邀请入口，不能留下空分组。状态：loading、default、partial-error、logged-out、god-view。

## profile `/me/profile`

显示头像、昵称、脱敏手机号和偏好摘要。当前 API 只明确 `GET /me`，没有资料编辑接口；编辑控件与保存动作必须标 `API pending`。不要生成换绑手机号、身份认证等未定义能力。

## credits 系列

- `credits`：主余额数字不带 `¥`；显示 tokensPerCredit 只读说明；入口为买积分和流水。
- `creditPackages`：套餐列表、渠道选择和支付动作。原生显示微信/支付宝/钱包，H5 只显示钱包。
- `creditLedger`：按日分组，consume 为负。接口有分页，但展示字段需以最终响应契约为准。
- `creditOrder`：支付状态和必要字段，不显示密钥或内部回调信息。

共同状态：loading、empty、error；支付页另有 selected、submitting、pending、success、failed。支付成功后从 creditSheet 场景返回时不能自动续跑旧对话。

## wallet 系列

- `wallet`：`availableAmount`、`frozenAmount`、`totalEarned` 三指标，全部带 `¥`；提现入口和记录入口。
- UI 元数据写有钱包流水，但 APP API 清单没有钱包流水接口。流水区标 `API pending`，不能以提现列表冒充钱包流水。
- `withdraw`：金额与支付宝账号；金额 > 0 且不超过可用余额；提交后只显示脱敏账号。
- `withdrawRecords`：分页列表与单条详情；只使用 accountMask。

状态：wallet loading/default/zero/error；withdraw empty/valid/insufficient/submitting/success/error；records loading/empty/default/error。

## authorizations `/me/auth`

四个平台列表。打开页面不假设有批量状态接口；按平台调用 status 时表现独立 loading。

- 京东：required=false、authorized=true，不展示授权或取消。
- 淘宝/拼多多/唯品会：按 status 返回展示去授权或取消授权。
- 取消前使用 confirmSheet；失败保留原状态。
- 不显示 PID、relationId、openId，也不出现美团。

设计 row-loading、authorized、not-authorized、not-required、query-error、revoking。

## invite `/me/invite`

布局：本月统计 → 8 位邀请码和邀请链接 → PosterCarousel → 分享/保存动作。`GET /invite` 与 `/invite/posters` 可独立 loading 和失败。

客户端把 inviteUrl 编码为二维码，并按模板 `qrX/qrY/qrWidth/qrHeight` 等比居中、保留静区。设计 composing、ready、template-swiping、no-template、compose-error、share-result。不得画出服务端生成海报按钮或把手机号/PID放进二维码。

## Figma 输出清单

先生成 CreditHero、MoneyHero、PackageCard、AuthRow、PosterCarousel 组件，再生成页面。积分和钱包必须并排做一次视觉审查，确认用户不会把两种资产误认为同一余额。

