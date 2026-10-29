# APP 页面（Android / iOS / H5）

契约（接口、SSE、字段）以 [`AGENTS.md`](../AGENTS.md)、[`api-app.md`](./api-app.md)、[`conversation.md`](./conversation.md) 为准。本文描述 **消费者端页面**：Android、iOS、H5（`apps/h5`）同一套。不含管理后台、官网、邀请落地页。禁止 Flutter。后台见 [`admin-ui.md`](./admin-ui.md)。

---

## 1. 全局风格配置


| 项     | 规定                                                                                                                      |
| ----- | ----------------------------------------------------------------------------------------------------------------------- |
| 项目背景  | Omino：用说话跨店选品，详情页「去购买」转链进第三方电商 APP。无购物车、无自营商品结算。积分买对话额度；返佣钱包可单向买积分，禁止积分提现或转回钱包。                                                   |
| 用户群体  | 20–50 岁中文消费者：对话、下单跳转、查单、积分、返佣、邀请、工单。                                                                                    |
| 产品类型  | 对话型导购 APP（消息流 + 商品卡 + 个人资产），不是电商首页、不是 AI 仪表盘。                                                                           |
| 品牌关键词 | 可信、干净、好开口、价签清楚、不花哨。                                                                                                     |
| 设计底座  | **shadcn/ui + Tailwind**（视觉语言与 Token）。H5 直接用 Tailwind 4.1.0；原生 Compose / SwiftUI **映射**同一套 Token，不引入 WebView 套整站。 |
| 视觉基调  | 浅色、白卡片、细分割线、单一强调色。默认方案 **V1**。                                                                                          |
| 密度    | 对话区偏紧（聊天气泡）；列表/表单舒适（行高 ≥ 48，点击 ≥ 44）。                                                                                   |
| 色彩策略  | 画布 / 卡片 / 正文 / 次要 / 边框 / `primary` / `destructive`（价签与危险）/ `muted`。积分数字用 `primary` 且无 `¥`；钱包数字近黑且必须 `¥`。一次只启用 §2 中一套方案。 |
| 图标风格  | Lucide 同源：线性、2px、圆角端点、24 栅格。Tab 选中用 `primary` 填充或描边加粗，禁止双色插画图标。                                                         |
| 动画    | 150–200ms ease；页面 push；Sheet 自底而上（≤ 92% 高）；流式用细进度 + 光标。禁止全屏 Lottie、弹跳弹簧。                                                |
| 参考风格  | shadcn/ui 默认（zinc + 一色强调）；对话密度参考微信；商品价签参考得物/京东商详。                                                                       |
| 额外约束  | **所有页面统一遵循这套风格，禁止私自变更配色、圆角、图标样式。** 另：商品卡禁止「购买/转链」；停止生成 ≠ 刷新/断线；无物流写「未知」；不展示 PID / relationId / 完整收款账号。P0 不做暗色。          |


**壳：** 登录后仅 Tab「对话 / 订单 / 我的」。登录、详情、提现表单、一切 Modal/Drawer **无 Tab**。

---



## 2. 视觉方案

一次只锁一套。未选定前实现 **V1**。换方案只换下表 Token，不改 §3 信息架构。


| ID  | 名称   | 画布        | 卡片        | primary       | 价签        | 圆角（卡/钮） | 气质     | 何时用      |
| --- | ---- | --------- | --------- | ------------- | --------- | ------- | ------ | -------- |
| V1  | 晴空零售 | `#F7F7F5` | `#FFFFFF` | `#0F8F7A`     | `#E11D48` | 12 / 16 | 正规店、青绿 | **默认**   |
| V2  | 瓷白杂志 | `#FFFFFF` | `#FFFFFF` | `#111111`（黑钮） | `#C41A32` | 8 / 8   | 留白、刊感  | 要拉开档次    |
| V3  | 暖纸市集 | `#F6F0E6` | `#FFFAF3` | `#C45C26`     | `#B42318` | 18 / 14 | 礼物/场景  | 主路径偏送礼   |
| V4  | 墨蓝账本 | `#F3F5F8` | `#FFFFFF` | `#1E3A5F`     | `#B91C1C` | 10 / 12 | 浅色账本   | 不信任提现时再切 |
| V5  | 青瓷细线 | `#F4FAF8` | `#FFFFFF` | `#0D7377`     | `#C81E4A` | 10 / 14 | 冷静、店招感 | 备选（偏服务台） |
| V6  | 柠黄价签 | `#FFFBEB` | `#FFFFFF` | `#CA8A04`     | `#DC2626` | 14 / 16 | 促销清楚   | 备选（价签优先） |
| V7  | 雾灰工具 | `#F4F4F5` | `#FAFAFA` | `#3F3F46`     | `#E11D48` | 8 / 10  | 极简工具   | 备选（少装饰） |
| V8  | 薄荷气泡 | `#F0FDFA` | `#FFFFFF` | `#14B8A6`     | `#F43F5E` | 16 / 20 | 对话软、年轻 | 备选（聊天气泡） |


共用：正文 `#171717`（V3 用 `#1F1712`）、次要 `#737373`、线 `#EBEBEA`（随方案微调）。禁止紫粉渐变、玻璃拟态、霓虹、深色赛博、第二套强调色。视觉 **待定**：未选定前实现 **V1**。

### 2.1 展示名候选（包名固定 `com.omino.shopai`）

商店/启动页用中文名，未锁定前工程可用 `Omino`。HTTPS 商店链上线时再定。

| 候选 | 气质 |
| --- | --- |
| 欧米诺 | 品牌音译，中性 |
| 问买 | 开口即搜 |
| 说买 | 语音优先 |
| 口令购 | 对话指令感 |
| 跨店问问 | 强调多平台 |
| 选品助手 | 功能直述 |
| 欧买 | 短、好念 |
| Omino | 英文包一致 |

---



## 3. 页面 & 组件元数据

列说明：`层级` = `模块 / 父`；`ID` 为代码命名；`Path` 仅 Page；无则 `—`。

### 3.1 Page


| type | 层级             | ID                | 名称     | Path                     | 入参 / Props                               | 目标                | 布局                    | 子单元                                                                                                  | 约束                                        |
| ---- | -------------- | ----------------- | ------ | ------------------------ | ---------------------------------------- | ----------------- | --------------------- | ---------------------------------------------------------------------------------------------------- | ----------------------------------------- |
| Page | 系统 / —         | `splash`          | 启动     | `/splash`                | `inviteCode?`                            | 读会话、上报已装 APP、分流   | 全屏字标                  | `BrandMark`                                                                                          | 无 Tab；有 token→`chat` 否则 `login`；落地唤起带邀请码  |
| Page | 认证 / —         | `login`           | 登录     | `/login`                 | `inviteCode?`                            | Demo 只进手机号；以后四入口 | 上标题下按钮栈               | `BtnPhone`（Demo 主入口）`BtnWechat` `LinkApple` `LinkGoogle` `LegalLinks`                          | Demo 隐藏微信/Apple/Google；无邮箱密码、无 SSO；成功进 Tab |
| Page | 认证 / `login`   | `loginPhone`      | 手机号验证码 | `/login/phone`           | `inviteCode?`                            | 短信登录              | 返回+表单                 | `PhoneField` `OtpGrid`                                                                               | 11 位；60s 倒计时；成功进 Tab                      |
| Page | 对话 / Tab       | `chat`            | 对话     | `/chat`                  | `conversationId?`                        | 说需求、出卡、澄清、HITL、停止 | 顶栏+消息流+Composer；底 Tab | `ChatTopBar` `MsgList` `Composer` `SuggestionChips` `GoodsCard` `CollectionCard` `ClarifyCard` `AuthCard` `ConfirmCard` `TicketReceipt` | JWT；无消息时拉推荐问题；生成中顶栏变「停止」；断线不 cancel；卡上无购买；合集卡进合集页 |
| Page | 对话 / `chat`    | `chatHistory`     | 会话列表   | `/chat/history`          | —                                        | 切会话、新对话           | 列表                    | `ConversationRow`                                                                                    | 「新对话」清空 id 回 `chat`                       |
| Page | 订单 / Tab       | `orders`          | 订单     | `/orders`                | `platform?` `orderStatus?`               | 看本人电商单            | 标题+两行 Chip+列表        | `PlatformChips` `StatusChips` `OrderRow`                                                             | 四平台码+六种订单状态；行上另标结算状态与用户返佣；空态不编物流；再次购买进详情不转链 |
| Page | 订单 / `orders`  | `orderDetail`     | 订单详情   | `/orders/:id`            | `id`                                     | 看一单状态             | 头图+字段表                | `OrderHeader` `InfoRows`                                                                             | 无物流→「暂无物流信息」；无转链钮                         |
| Page | 我的 / Tab       | `me`              | 我的     | `/me`                    | —                                        | 进资产与账号            | 头+分组列表                | `MeHeader` `inboxBadge` `GroupGoods` `GroupAsset` `GroupAccount` `GroupSupport`                      | 脚注「积分与返佣分户」；商品分组：浏览/收藏；站内信未读红点     |
| Page | 我的 / `me`      | `profile`         | 个人资料   | `/me/profile`            | —                                        | 看/改昵称头像           | 表单                    | `Avatar` `Field`                                                                                     | 手机脱敏；偏好只读摘要                               |
| Page | 我的 / `me`      | `credits`         | 对话积分   | `/me/credits`            | —                                        | 看余额、去买            | 大数字+说明+双入口            | `BalanceHero` `BtnBuy` `LinkLedger`                                                                  | 数字无 `¥`；`tokensPerCredit` 只读              |
| Page | 积分 / `credits` | `creditPackages`  | 积分套餐   | `/me/credits/packages`   | `reason?`                                | 选套餐支付             | 套餐列表+渠道+支付            | `PackageCard` `ChannelRow`                                                                           | 复用于 `creditSheet`；渠道 wechat / alipay / **余额** |
| Page | 积分 / `credits` | `creditLedger`    | 积分流水   | `/me/credits/ledger`     | `page?`                                  | 对账                | 按日分组列表                | `LedgerRow`                                                                                          | consume 为负                                |
| Page | 积分 / `credits` | `creditOrder`     | 积分支付单  | `/me/credits/orders/:id` | `id`                                     | 看支付状态             | 字段表                   | `StatusBadge`                                                                                        | 无密钥                                       |
| Page | 我的 / `me`      | `wallet`          | 返佣钱包   | `/me/wallet`             | —                                        | 看现金返佣             | 三指标+流水+提现             | `MoneyHero` `LedgerRow`                                                                              | 必须 `¥`；视觉与积分页区分                           |
| Page | 钱包 / `wallet`  | `withdraw`        | 申请提现   | `/me/wallet/withdraw`    | —                                        | 提到支付宝             | 金额+账号表单               | `AmountField` `PayeeField`                                                                           | 提交后只展示 mask                               |
| Page | 钱包 / `wallet`  | `withdrawRecords` | 提现记录   | `/me/wallet/records`     | —                                        | 看申请进度             | 列表                    | `WithdrawRow`                                                                                        | 不回完整账号                                    |
| Page | 我的 / `me`      | `goodsHistory`    | 浏览记录   | `/me/goods/history`      | —                                        | 看点过的商品            | 列表                    | `GoodsCard`                                                                                          | 来自 `GET /goods/history`；点行进详情；无购买钮           |
| Page | 我的 / `me`      | `goodsFavorites`  | 收藏     | `/me/goods/favorites`    | —                                        | 看收藏               | 列表                    | `GoodsCard`                                                                                          | 取消收藏走 DELETE；点行进详情                         |
| Page | 商品 / `chat`    | `goodsCollection` | 商品合集   | `/goods/collections/:id` | `id`                                     | 看一轮推荐的全部商品      | 标题+网格/列表+移除         | `GoodsCard` `BtnRemove`                                                                              | 仅本人；移除不进详情；空合集可回对话                         |
| Page | 我的 / `me`      | `authorizations`  | 联盟授权   | `/me/auth`               | —                                        | 授权/取消四平台          | 四行列表                  | `AuthRow`                                                                                            | 去授权→`authSheet`；取消→`confirmSheet`；无 PID；无美团 |
| Page | 我的 / `me`      | `invite`          | 邀请     | `/me/invite`             | —                                        | 传播邀请、分享海报         | 统计+码/链+横向海报         | `InviteStats` `CodeBlock` `PosterCarousel`                                                           | 码 8 位；多模板左右滑；端上贴二维码后分享微信/QQ/相册           |
| Page | 我的 / `me`      | `tickets`         | 工单列表   | `/me/tickets`            | `status?`                                | 看我的单              | 筛选+列表                 | `TicketRow`                                                                                          | 不提供页内新建（对话建单）                             |
| Page | 工单 / `tickets` | `ticketDetail`    | 工单详情   | `/me/tickets/:id`        | `id`                                     | 看时间线、补证据          | 状态头+时间线+底栏            | `TicketMeta` `TicketLog` `EvidenceComposer`                                                          | 未关单才可补图；回复≠已修好                            |
| Page | 我的 / `me`      | `inbox`           | 站内信    | `/me/inbox`              | —                                        | 看私信                | 列表                    | `InboxRow`                                                                                           | 未读红点来自 unread-count；点行标已读                    |
| Page | 我的 / `me`      | `settings`        | 设置     | `/me/settings`           | —                                        | 显示/关于/账号          | 分组列表                  | 见下「设置分组」                                                                                         | 无 Tab；H5 上帝视角不可注销生产账号（`gv=1` 禁用注销）        |
| Page | 设置 / `settings` | `settingsDisplay` | 显示     | `/me/settings/display`   | —                                        | 风格与字号             | 列表+预览                 | `ThemePicker` `FontScale`                                                                            | 仅本地；风格 V1–V8；字号 sm/md/lg                    |
| Page | 设置 / `settings` | `settingsPush`    | 推送设置   | `/me/settings/push`      | —                                        | 开关推送              | 单开关                   | `Switch`                                                                                             | PATCH `/me/push`                            |
| Page | 设置 / `settings` | `settingsAbout`   | 关于     | `/me/settings/about`     | —                                        | 版本与协议             | 列表                    | `VersionRow` `LinkAgreement` `LinkPrivacy` `BtnCheckUpdate`                                          | 协议打开 legal URL；检查更新走 check-update           |
| Page | 系统 / —         | `forceUpdate`     | 强制更新   | `/update`                | `release`                                | 阻断旧版              | 全屏说明+下载               | `Changelog` `BtnDownload`                                                                            | `action=force` 不可返回业务页                       |
| Page | 商品 / `chat`    | `goodsDetail`     | 商品详情   | `/goods/:goodsId`        | `goodsId` 或 `platform`+`platformGoodsId` | 决策购买              | 图廊+信息+底栏 CTA          | `Gallery` `PriceBlock` `SalesMeta` `BtnFav` `BtnBuy`                                                             | **唯一**「去购买」走转链；销量/评价数为空则不显示 0；`goodsUrl` 不作购买按钮；可收藏；NEED_AUTH→`authSheet` |




### 3.2 Component


| type      | 层级             | ID              | 名称   | Path | 入参 / Props                       | 目标         | 布局         | 子单元                    | 约束                          |
| --------- | -------------- | --------------- | ---- | ---- | -------------------------------- | ---------- | ---------- | ---------------------- | --------------------------- |
| Component | 壳 / Tab 页      | `tabBar`        | 底栏   | —    | `current`                        | 三 Tab 切换   | 三等分图标+字    | —                      | 仅 `chat` `orders` `me`      |
| Component | 对话 / `chat`    | `chatTopBar`    | 对话顶栏 | —    | `title` `credits` `generating`   | 积分入口、历史、停止 | 左标题右一控件    | `CreditChip`           | 生成中只留停止                     |
| Component | 对话 / `chat`    | `composer`      | 输入栏  | —    | `text` `generating`              | 发文字/语音/扩展  | `+` 框 麦 发送 | —                      | 生成中禁用发送或只停止；返回手势 ≠ cancel   |
| Component | 对话 / `chat`    | `suggestionChips` | 推荐问题 | — | `items[]`（`id` `text`） | 空对话时一键发问 | Composer 上方横滑 Chip | — | 只在消息列表为空时显示；点击即 `POST turns`；数据来自 `GET /chat/suggestions` |
| Component | 对话 / `msgList` | `goodsCard`     | 商品卡  | —    | `GoodsCard` 字段                   | 进详情        | 左图右文       | `PlatformPill` `Price` | 整卡点进 `goodsDetail`；**无购买钮**；有销量/评价数才展示，空不显示 0 |
| Component | 对话 / `msgList` | `collectionCard` | 合集卡 | —    | `collectionId` `title` `total` `covers[]` | 进合集页 | 封面拼贴+标题+件数 | `CoverGrid` | 点卡进 `goodsCollection`；对话里不逐条刷 >6 张 |
| Component | 对话 / `msgList` | `clarifyCard`   | 澄清卡  | —    | `question` `missingSlots[]`      | 补槽         | 问句+Chip    | —                      | Chip 填输入框，不自动发、不搜品          |
| Component | 对话 / `msgList` | `authCard`      | 授权摘要 | —    | `platform` `interruptId`         | 打开授权       | 文案+按钮      | —                      | 打开 `authSheet`；本 run 已结束    |
| Component | 对话 / `msgList` | `confirmCard`   | 确认摘要 | —    | `prompt` `interruptId`           | 打开确认       | 文案+按钮      | —                      | 打开 `confirmSheet`           |
| Component | 对话 / `msgList` | `ticketReceipt` | 工单回执 | —    | `ticketId` `status`              | 进工单        | 小卡         | —                      | 文案：只建单不假装修好                 |
| Component | 订单 / `orders`  | `orderRow`      | 订单行  | —    | 订单瘦字段                            | 进详情 / 再买    | 图+标题+订单状态+用户返佣+结算状态+实付+再次购买 | `PlatformPill` `BtnBuyAgain` | 点行进详情；再次购买仅当有 `goodsId`，进 `goodsDetail`，禁止行内转链 |
| Component | 我的 / `me`      | `inboxBadge`    | 未读红点 | —    | `count`                            | 进站内信       | 数字角标       | —                      | count=0 不显示                  |
| Component | H5 / 壳         | `godViewBar`    | 上帝视角条 | —    | `userId` `nickname`                | 标明正在看谁     | 顶栏常驻警告     | —                      | JWT `gv=1` 时必须显示，不可关         |
| Component | 积分 / 多页        | `packageCard`   | 套餐卡  | —    | `id name credits price selected` | 选套餐        | 三列信息+选中描边  | —                      | 不含成本价                       |
| Component | 邀请 / `invite`  | `posterCarousel` | 海报切换 | —    | 生效中模板列表                    | 左右滑选模板、贴码、分享 | 横滑全幅     | `PosterSlide` `Dots`     | 客户端生成二维码贴进 `qr*` 矩形；仅落地 URL |




### 3.3 Modal / Drawer


| type   | 层级                           | ID              | 名称     | Path | 入参 / Props                                   | 目标         | 布局          | 子单元                   | 约束                               |
| ------ | ---------------------------- | --------------- | ------ | ---- | -------------------------------------------- | ---------- | ----------- | --------------------- | -------------------------------- |
| Drawer | 对话 / `chat`                  | `attachSheet`   | 输入扩展   | —    | —                                            | 拍照/相册/粘贴链接 | 底 Sheet 三行  | —                     | 图先上传再 `inputType=image`          |
| Modal  | 授权 / `chat`或`authorizations` | `authSheet`     | 联盟授权   | —    | `platform` `authUrl` `interruptId`           | 打开官方授权页    | 底 Sheet     | `BtnOpen` `BtnCancel` | 非支付；取消不 resume                   |
| Modal  | 授权 / 同上                      | `confirmSheet`  | 确认取消授权 | —    | `prompt` `interruptId`                       | HITL 确认    | 底 Sheet 双钮  | —                     | confirm / cancel                 |
| Modal  | 积分 / `chat`                  | `creditSheet`   | 购积分卡   | —    | `reason` `balance` `packages[]`              | 余额不足时买套餐   | 遮罩+套餐列表     | `packageCard`         | 非 HITL；关 Sheet 保留已流式文字；支付成功不自动续跑 |
| Modal  | 商品 / `goodsDetail`           | `openExternal`  | 打开第三方  | —    | `deeplink` `universalLink` `fallbackHttpUrl` | 去电商 APP    | 系统打开，无自绘浏览器 | —                     | 未安装走 H5                          |
| Modal  | 设置 / `settings`              | `logoutConfirm` | 退出确认   | —    | —                                            | 防误触        | 双钮 Sheet    | —                     | 确认→`login`                       |
| Modal  | 设置 / `settings`              | `deleteConfirm` | 注销确认   | —    | —                                            | 注销账号       | 输入 DELETE+双钮 | —                     | 调 `POST /me/delete`；不可逆软删           |
| Modal  | 系统 / 启动                     | `optionalUpdate` | 可选更新  | —    | `release`                                    | 提示升级       | 底 Sheet      | `BtnLater` `BtnUpdate` | `action=optional`；稍后仍进首页             |
| Modal  | 系统 / 启动                     | `announcementSheet` | 公告 | —    | `announcement`                               | 展示运营公告     | 底 Sheet      | —                     | 多条可滑；已读存本地                         |
| Modal  | 系统 / 多页                      | `toast`         | 轻提示    | —    | `text`                                       | 失败/成功一句    | 底中单行 2s     | —                     | 不堆系统 Alert                       |


登录成功后根为 Tab：`/chat` · `/orders` · `/me`。主路径：`login` → `chat` → `goodsDetail` →（未授权）`authSheet`；（积分不足）`creditSheet` → `creditPackages`。

**设置页分组（`settings` 本体）**

| 分组 | 行 | 去向 |
| --- | --- | --- |
| 显示 | 风格、字体大小 | `settingsDisplay` |
| 商品 | 浏览记录、收藏 | `goodsHistory` / `goodsFavorites` |
| 关于 | 用户协议、隐私政策、关于、检查更新 | 协议外链 / `settingsAbout` |
| 通知 | 推送设置 | `settingsPush` |
| 账号 | 注销、退出登录 | `deleteConfirm` / `logoutConfirm` |

H5 上帝视角：Admin 取 token 后进入 `/chat`，顶栏 `godViewBar`；请求头 `X-Client-Type: h5`、`X-App-Channel: web`。