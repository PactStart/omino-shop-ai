# 管理后台页面（`apps/admin-web`）

契约以 [`api-admin.md`](./api-admin.md)、[`AGENTS.md`](../AGENTS.md) 为准。本文只描述 **运营后台 UI**：信息架构与组件，不含消费者 H5 / 官网 / 落地页。

登录必须 omino-sso OIDC + PKCE S256（推荐 BFF，浏览器不持 token）。禁止账号密码表。菜单、按钮和数据按 [`admin-access.md`](./admin-access.md) 的权限点显示。

---

## 1. 全局风格

| 项 | 规定 |
| --- | --- |
| 产品类型 | 内部运营后台：表格、表单、筛选、观测。不是营销站。 |
| 技术 | Vue 3 + Element Plus `2.9.1` + 与 `omino-sso` admin 同族版本（见 `AGENTS.md` §2.4）。 |
| 壳 | 左栏 + 顶栏（运营名、主题、登出）+ 主区。深链可刷新。分组可折叠。左栏可收起：收起后只留一级图标，悬停显示该项文字。菜单超出视口时在品牌行下方上下滚动，品牌行保持可见。图标用已锁定的 `@element-plus/icons-vue`。顶栏在浅色与深色之间切换，选择记在本机 `admin.theme`（`light` / `dark`），刷新后保持；深色使用 Element Plus 自带的 dark 变量。登出后进入门户「我的应用」，统一身份会话保持；本应用会话已作废，旧 Cookie 不能再打开业务页。门户未登录时由其自己进入登录页。 |
| 密度 | 表格舒适；筛选一行；主按钮 Primary，危险操作 `el-button type="danger"`。 |
| 禁 | 不把联盟 PID / 密钥 / 完整收款账号画进表格；金额字符串两位；时间 ISO 展示为本地。 |
| 用户 | 列表进详情；详情左侧菜单看该账号在 App 侧的全部运营可见数据。 |
| 上帝视角 | 不在左栏。用户列表操作栏「用户视角」从右侧打开抽屉。`prod` 默认点了返回 `40303`。 |

---

## 2. 信息架构（左栏）

顺序固定。未排到 Spec Kit 的页可先空壳，禁止自造接口。

| 分组 | 菜单 | 路由 ID | Path |
| --- | --- | --- | --- |
| 概览 | 工作台 | `dashboard` | `/` |
| 概览 | 漏斗 | `statsFunnel` | `/stats/funnel` |
| 概览 | 三本账 | `statsLedger` | `/stats/ledger` |
| 概览 | 风险 | `statsRisk` | `/stats/risk` |
| 交易 | 电商订单 | `orders` | `/orders` |
| 交易 | 同步游标 | `orderSync` | `/orders/sync` |
| 用户 | 用户列表 | `users` | `/users` |
| 用户 | 会话观测 | `conversations` | `/conversations` |
| 用户 | 工单 | `tickets` | `/tickets` |
| 资产 | 提现审核 | `withdrawals` | `/withdrawals` |
| 资产 | 积分套餐 | `creditPackages` | `/credits/packages` |
| 资产 | 积分订单 | `creditOrders` | `/credits/orders` |
| 资产 | 积分流水 | `creditLedger` | `/credits/ledger` |
| 增长 | 邀请海报 | `invitePosters` | `/invite-posters` |
| 运营 | 版本发布 | `releases` | `/releases` |
| 运营 | 特殊配置 | `runtimeConfigs` | `/runtime-configs` |
| 运营 | 公告 | `announcements` | `/announcements` |
| 运营 | 商城布局 | `mallLayout` | `/mall-layout` |
| 运营 | 站内信 | `inbox` | `/inbox` |
| 对话 | 推荐问题 | `chatSuggestions` | `/chat-suggestions` |
| 对话 | 提示词 | `agentPrompts` | `/agent-prompts` |
| 对话 | 模型分流 | `llmModels` | `/llm-models` |
| 内容 | 知识库 | `kb` | `/kb` |
| 系统 | 审计 | `audit` | `/audit` |

另：`loginCallback` `/login/callback` 无壳。

---

## 3. 页面元数据

`ID` 是代码命名，`Path` 只给页面。消费者页面见 [`ui/README.md`](./ui/README.md)。

### 3.1 Page

| ID | 名称 | Path | 目标 | 布局 | 约束 |
| --- | --- | --- | --- | --- | --- |
| `loginCallback` | SSO 回调 | `/login/callback` | 换 Cookie | 空白+转圈 | 失败去离线/重登 |
| `dashboard` | 工作台 | `/` | 同步失败、开单量、待审提现，以及每日指标 | 卡片+折线 | 只读；不编造数。指标布局与口径见 [`ops-metrics.md`](./ops-metrics.md) |
| `statsFunnel` | 漏斗 | `/stats/funnel` | 分平台看转链到入账 | 四列条形 | 见 [`ops-metrics.md`](./ops-metrics.md) |
| `statsLedger` | 三本账 | `/stats/ledger` | 佣金、钱包、积分 | 三列 | 见 [`ops-metrics.md`](./ops-metrics.md) |
| `statsRisk` | 风险 | `/stats/risk` | 看还没处理的异常单和账 | 表 | 见 [`ops-metrics.md`](./ops-metrics.md) |
| `users` | 用户列表 | `/users` | 找人、进详情、打开该用户的模拟屏 | 筛选+表+右侧抽屉 | 点用户编号进 `userDetail`。操作栏「用户视角」打开抽屉。手机列表脱敏 |
| `userDetail` | 用户详情 | `/users/:id` | 看该用户全部运营可见信息 | 左菜单+右内容 | 见 §3.4；无 JWT/密钥/PID |
| `orders` | 电商订单 | `/orders` | 按平台/平台订单号/订单状态/结算状态/用户查 | 筛选+表 | 表不展示本系统订单号。列顺序为平台、平台单号、用户，其后是商品和金额。商品列左侧是主图、右侧是名称；没有图时左侧留空位。付款时间、完成时间在下单时间后面，没有值的格子留空。默认每页 10 条。点行仍进详情。无联盟原始 JSON；表上同时能看媒体返佣与用户分成 |
| `orderDetail` | 订单详情 | `/orders/:id` | 看一单 | 描述列表 | 无物流写未知；已结算行显示钱包流水 ID。付款时间、完成时间、`extras` 为空则不出现这一行 |
| `orderSync` | 同步游标 | `/orders/sync` | 看水位、改水位、同步 | 四行平台 | 右上角刷新重新拉取四行。最近错误显示这次失败的原因。操作栏是「设置」和「同步」。每行可改下单水位和更新水位，可清空。改水位和同步都写审计。同步只触发，拉单在 APP 内异步追平；无美团 |
| `conversations` | 会话 | `/conversations` | 观测对话 | 表 | 只读；无密钥 |
| `conversationDetail` | 会话消息 | `/conversations/:id` | 看消息 | 时间线 | 不改用户话 |
| `tickets` | 工单 | `/tickets` | 处理反馈 | 筛选+表 | 改状态必须出 log |
| `ticketDetail` | 工单详情 | `/tickets/:id` | 时间线/回复/改派 | 左态右 log | 回复≠关单 |
| `withdrawals` | 提现 | `/withdrawals` | 审核 | 表+抽屉 | 只展示 `accountMask` |
| `creditPackages` | 积分套餐 | `/credits/packages` | 上下架 | 表+抽屉表单 | 禁止物理删被引用套餐 |
| `creditOrders` | 积分支付单 | `/credits/orders` | 对账 | 表 | 无支付密钥 |
| `creditLedger` | 积分流水 | `/credits/ledger` | 全站流水 | 表+调账对话框 | 调账必填备注+审计 |
| `creditRate` | Token 比例 | 套餐页只读条 | 看 Nacos 当前值 | 文案 | 改去 Nacos |
| `invitePosters` | 海报模板 | `/invite-posters` | 上传模板、框二维码区、生效期 | 列表或卡片，可按状态筛 | 列表含模板链接和生效时间。卡片一行四张，图下是名称、状态、生效时间和编辑。**预览在编辑页用浏览器贴码**；无服务端合成 |
| `invitePosterEdit` | 编辑模板 | `/invite-posters/:id` | 标定矩形 | 画布+数字框+日期 | `startsAt`/`endsAt` 可空 |
| `releases` | 版本 | `/releases` | 发渠道包 | 表；Android 一行一渠道 | `versionCode` 输入 `yyyyMMdd`。说明可选纯文本或有限富文本，对应 `format` |
| `runtimeConfigs` | 特殊配置 | `/runtime-configs` | 渠道/端/版本行为 | 表+JSON 表单 | 只允许已锁 payload 键 |
| `announcements` | 公告 | `/announcements` | 定向投放 | 表+抽屉 | 端/渠道多选；版本范围日期码。正文可选纯文本或有限富文本，对应 `format` |
| `mallLayout` | 商城布局 | `/mall-layout` | 配 banner 和金刚区 | 版位参数 + 两张表 + 抽屉 | 不新增版位。金刚图标必须选分类。点击参数按类型填写。不物理删除 |
| `inbox` | 站内信 | `/inbox` | 发给一个人 | 表单 userId+标题正文 | 禁止全站群发。正文可选纯文本或有限富文本，对应 `format`；列表显示去掉标签后的文字 |
| `chatSuggestions` | 推荐问题 | `/chat-suggestions` | 配新对话空态问题 | 表+抽屉 | 上架最多 12；下架不物理删 |
| `agentPrompts` | 提示词 | `/agent-prompts` | 改八条固定提示词 | 左 key 右编辑器 | 不能增删 key；保存后下一轮对话生效 |
| `llmModels` | 模型分流 | `/llm-models` | 多个模型与权重 | 表+抽屉 | 密钥只写不回显；列表只显示末 4 位 |
| `kb` | 知识库 | `/kb` | 规则正文 | 表+编辑器 | retired 不停物理删若仍检索 |
| `userLive` | 用户视角抽屉 | 用户列表内 | 用短时凭证打开该用户的 H5 首页 | 右侧抽屉 + 手机框 | 没有独立路由。抽屉只留标题，标题下居中放一台大号手机，按竖屏比例，不横向拉满。不放用户名、编号和说明。本地地址是 `http://localhost:5177/chat`，不要用 `127.0.0.1`。令牌只放进模拟屏地址，页面不展示。prod 未开启时抽屉里是 `40303` |
| `audit` | 审计 | `/audit` | 追操作 | 表 | 无密钥 |

### 3.2 海报编辑（重点）

- 上传模板原图；叠加半透明矩形，拖改 `qrX/Y/W/H`，右侧同步数字。
- 预览：用占位邀请 URL 在 **浏览器 Canvas** 贴码，与消费者端算法一致。
- 生效：`el-date-picker` 日期时间范围，可清空。列表和卡片用 Tag 显示「未开始 / 生效中 / 已过期 / 已下架」。
- 列表列：名称、模板链接、状态、生效时间范围、排序、编辑。模板链接取 `imageObjectKey`；`http` / `https` 可点开，对象键只显示文字。生效时间空的一侧写「不限」，两侧都空则整段为「不限」。
- 状态筛选在本页完成，选项与四个 Tag 相同，清空后看全部。不另打接口。
- 同一批数据可切成卡片，固定一行四张。卡片直接显示模板图；图下是名称、状态、生效时间和编辑。图区宽高比 `9:16`，与默认画布 `750×1334` 相同。没有可显示的图片地址时，图区写「无预览」。

### 3.3 版本发布（重点）

- `versionCode` 占位符 `20261029`，校验八位日期。
- Android：`channel` 必填下拉（Nacos 白名单）。iOS 锁 `appstore`，H5 锁 `web`。
- 同一天同渠道已有行：表单提示改这一行，不要再新建。
- `force_update` 开关；`published` 后才出现在检查更新里。

### 3.4 用户详情（重点）

壳：顶栏固定该用户 `nickname` / `id`。**左侧是带图标的菜单，右侧是内容。** 右区只带当前 `userId` 拉数。禁止展示 JWT、联盟 PID、完整收款账号。用户视角不在这一页，从用户列表操作栏进入。

| 菜单 ID | 名称 | 右区 | 数据 |
| --- | --- | --- | --- |
| `udProfile` | 基本信息 | 描述列表 | id、昵称、完整手机、状态、locale、邀请码、邀请人 id、推送开关、注册和更新时间 |
| `udAuth` | 联盟授权 | 四行表 | 授权状态；无 PID / relationId |
| `udWallet` | 钱包流水 | 表 | 类型、金额、备注、时间 |
| `udOrders` | 电商订单 | 表 | 该用户电商单。列与订单列表一致，商品列同样左图右名称。不展示本系统订单号。点行进订单详情 |
| `udCreditOrders` | 积分订单 | 表 | 该用户积分支付单 |
| `udChat` | 对话 | 表 | 该用户会话；点进会话详情 |
| `udHistory` | 商品足迹 | 卡片，一行 4 个 | 图、名称、时间。只读 |
| `udFavorites` | 商品收藏 | 卡片，一行 4 个 | 图、名称、时间。只读 |
| `udCollections` | 商品合集 | 卡片，一行 6 个 | 名称和时间。点击用抽屉展示合集内商品，不替用户删除 |
| `udTickets` | 工单 | 表 | 该用户的工单。分类、内容、优先级、状态、更新时间。点行进工单详情 |
| `udInvites` | 邀请记录 | 表 | 该用户邀请的人。昵称、脱敏手机、状态、生效时间、邀请时间。点昵称进对方详情 |
| `udInbox` | 站内信 | 表 | 发给该用户的私信。标题、去掉标签后的正文、时间。只读 |

深链：`/users/:id` 默认基本信息。子菜单用 query `tab=`，刷新不丢当前菜单。列表筛：手机精确、邀请码、userId、昵称模糊、状态。

---

## 4. 主路径

运营登录（SSO）→ 工作台 → **用户列表 → 用户详情（左侧菜单看全量）**。发版本 / 配渠道行为 / 发公告或私信。海报：上传 → 框二维码 → 看 Canvas 预览 → 设生效期 → 上架。用户视角：用户列表操作栏 → 右侧抽屉签发短时凭证 → 手机框打开 H5 首页。
