# 第 4 层：核心用户流程

本层用于生成 FigJam 流程图和 Figma 可点击原型。节点名使用 Page ID，条件写在连线上；不得把接口名画成用户可见页面。

## 1. 启动与登录

```text
splash
├── force update → forceUpdate（阻断）
├── optional update → optionalUpdate → 继续
├── announcement → announcementSheet → 继续
├── valid token → chat
└── no token → login → loginPhone
                      ├── SMS error → 原地错误
                      └── success → chat
```

关键接口：`GET /app/check-update`、`GET /announcements`、`GET /legal`、`POST /auth/sms/send`、`POST /auth/sms/login`。邀请唤起带来的 `inviteCode` 必须保留到首次登录。

## 2. 对话选品

```text
chat empty → suggestion or composer → POST turn → generating
generating ├── text/product cards → done → idle
           ├── clarify → user fills composer → new turn
           ├── auth card → run ends → authSheet → external auth → new HITL turn
           ├── confirm card → run ends → confirmSheet → new HITL turn
           ├── credit card → creditSheet → payment → user starts new turn
           ├── user stop → cancel → cancelled
           └── SSE disconnect → reconnect with Last-Event-ID（不是 cancel）
product card → goodsDetail → favorite or convert-link
collection card → goodsCollection → goodsDetail
```

原型必须分别表现 `generating`、`reconnecting`、`cancelled`、`auth interrupted`、`credit exhausted`。HITL 恢复和支付后继续都会创建新 run；不能把旧 run 画成暂停后原地继续。

## 3. 商品购买与授权

```text
goodsDetail → 去购买 → convert-link
├── success → openExternal
├── NEED_AUTH → authSheet → 官方授权页 → 返回详情 → 再次去购买
└── upstream/error → 原页 Toast/错误反馈
```

原生打开顺序为 deeplink → universalLink → fallbackHttpUrl；H5 只开 fallbackHttpUrl，没有时开 clickUrl。商品列表、对话卡和订单行均不能直接转链。

## 4. 订单

```text
orders → filters → orders refreshed
order row with goodsId → goodsDetail → 去购买
order row without goodsId → 卡片不可点
```

订单状态和结算状态同时存在。消费者不进入订单详情。

## 5. 积分、钱包与支付

```text
credits → creditPackages → choose package/channel → create credit order
├── native wechat/alipay → external payment → creditOrder
└── wallet → immediate result → creditOrder

wallet → withdraw → submit → withdrawRecords
wallet → buy credits → creditPackages(wallet)
```

H5 的套餐页和积分 Sheet 只显示钱包渠道。资产视觉必须持续区分：积分不带 `¥`，钱包带 `¥`；禁止画出积分提现或积分转钱包路径。

## 6. 邀请分享

```text
me → invite → load stats + poster templates
→ client creates QR from inviteUrl
→ place QR in template rectangle
→ swipe template → share/save → telemetry poster_share
```

设计需包含模板加载、二维码合成中、合成失败、无模板和分享成功/失败。服务端不返回合成图，不设计“服务端生成海报”步骤。

## 7. 授权管理与取消

```text
me → authorizations → status(platform)
├── not required (JD) → 已可用，无授权动作
├── not authorized → authSheet → external auth
└── authorized → confirmSheet → revoke
                      ├── success → refresh row
                      └── failure → keep authorized + error
```

不得显示 PID、relationId、openId。拼多多取消后再次查询可能重新显示已授权，文案不能承诺平台侧永久解绑。

## 8. 设置、注销与版本更新

```text
settings → display → local theme/font update → all visible UI refresh
settings → push → PATCH toggle → success/revert on error
settings → about → legal external page / check update
settings → logoutConfirm → login
settings → deleteConfirm(DELETE) → delete → login
```

`gv=1` 的 H5 禁用注销。强制更新不可返回业务页面；可选更新允许稍后。

## 9. 原型验收

- 每个流程有成功、失败、取消、返回和重复点击结果。
- 外部授权、支付、第三方 APP 打开使用明确的外部节点。
- 网络断线、页面返回和业务取消是三条不同分支。
- 流程图中的页面、动作和接口能在第五层页面文档中逐项找到。

