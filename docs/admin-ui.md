# 管理后台页面（`apps/admin-web`）

契约以 [`api-admin.md`](./api-admin.md)、[`AGENTS.md`](../AGENTS.md) 为准。本文只描述 **运营后台 UI**：信息架构与组件，不含消费者 H5 / 官网 / 落地页。

登录必须 omino-sso OIDC + PKCE S256（推荐 BFF，浏览器不持 token）。禁止账号密码表。

---

## 1. 全局风格

| 项 | 规定 |
| --- | --- |
| 产品类型 | 内部运营后台：表格、表单、筛选、观测。不是营销站。 |
| 技术 | Vue 3 + Element Plus `2.9.1` + 与 `omino-sso` admin 同族版本（见 `AGENTS.md` §2.4）。 |
| 壳 | 左栏菜单 + 顶栏（运营名、登出）+ 主区。深链可刷新。 |
| 密度 | 表格舒适；筛选一行；主按钮 Primary，危险操作 `el-button type="danger"`。 |
| 禁 | 不把联盟 PID / 密钥 / 完整收款账号画进表格；金额字符串两位；时间 ISO 展示为本地。 |
| 用户 | 列表进详情；详情用 **用户视角菜单** 看该账号在 App 侧的全部运营可见数据。上帝视角（H5）是其中一项，不是唯一入口。 |
| 上帝视角 | 菜单名「用户视角」；`prod` 默认入口隐藏或点了返回 `40303`。 |

---

## 2. 信息架构（左栏）

顺序固定。未排到 Spec Kit 的页可先空壳，禁止自造接口。

| 分组 | 菜单 | 路由 ID | Path |
| --- | --- | --- | --- |
| 概览 | 工作台 | `dashboard` | `/` |
| 交易 | 电商订单 | `orders` | `/orders` |
| 交易 | 同步游标 | `orderSync` | `/orders/sync` |
| 用户 | 用户列表 | `users` | `/users` |
| 用户 | 用户视角 | `godView` | `/god-view` |
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
| 运营 | 站内信 | `inbox` | `/inbox` |
| 对话 | 推荐问题 | `chatSuggestions` | `/chat-suggestions` |
| 对话 | 提示词 | `agentPrompts` | `/agent-prompts` |
| 对话 | 模型分流 | `llmModels` | `/llm-models` |
| 内容 | 知识库 | `kb` | `/kb` |
| 系统 | 审计 | `audit` | `/audit` |

另：`loginCallback` `/login/callback` 无壳。

---

## 3. 页面元数据

列与 [`app-ui.md`](./app-ui.md) 相同口径。

### 3.1 Page

| ID | 名称 | Path | 目标 | 布局 | 约束 |
| --- | --- | --- | --- | --- | --- |
| `loginCallback` | SSO 回调 | `/login/callback` | 换 Cookie | 空白+转圈 | 失败去离线/重登 |
| `dashboard` | 工作台 | `/` | 同步失败、开单量、待审提现 | 卡片+短表 | 只读；不编造数 |
| `users` | 用户列表 | `/users` | 找人、进详情 | 筛选+表 | 点行进 `userDetail`；手机列表脱敏 |
| `userDetail` | 用户详情 | `/users/:id` | 看该用户全部运营可见信息 | 左用户视角菜单+右内容 | 见 §3.4；无 JWT/密钥/PID |
| `orders` | 电商订单 | `/orders` | 按平台/订单状态/结算状态/用户查 | 筛选+表 | 无联盟原始 JSON；表上同时能看媒体返佣与用户分成 |
| `orderDetail` | 订单详情 | `/orders/:id` | 看一单 | 描述列表 | 无物流写未知；已结算行显示钱包流水 ID |
| `orderSync` | 同步游标 | `/orders/sync` | 看水位、重试 | 四行平台 | 重试写审计；无美团 |
| `conversations` | 会话 | `/conversations` | 观测对话 | 表 | 只读；无密钥 |
| `conversationDetail` | 会话消息 | `/conversations/:id` | 看消息 | 时间线 | 不改用户话 |
| `tickets` | 工单 | `/tickets` | 处理反馈 | 筛选+表 | 改状态必须出 log |
| `ticketDetail` | 工单详情 | `/tickets/:id` | 时间线/回复/改派 | 左态右 log | 回复≠关单 |
| `withdrawals` | 提现 | `/withdrawals` | 审核 | 表+抽屉 | 只展示 `accountMask` |
| `creditPackages` | 积分套餐 | `/credits/packages` | 上下架 | 表+抽屉表单 | 禁止物理删被引用套餐 |
| `creditOrders` | 积分支付单 | `/credits/orders` | 对账 | 表 | 无支付密钥 |
| `creditLedger` | 积分流水 | `/credits/ledger` | 全站流水 | 表+调账对话框 | 调账必填备注+审计 |
| `creditRate` | Token 比例 | 套餐页只读条 | 看 Nacos 当前值 | 文案 | 改去 Nacos |
| `invitePosters` | 海报模板 | `/invite-posters` | 上传模板、框二维码区、生效期 | 左表右预览 | **预览在浏览器贴码**；无服务端合成 |
| `invitePosterEdit` | 编辑模板 | `/invite-posters/:id` | 标定矩形 | 画布+数字框+日期 | `startsAt`/`endsAt` 可空 |
| `releases` | 版本 | `/releases` | 发渠道包 | 表；Android 一行一渠道 | `versionCode` 输入 `yyyyMMdd` |
| `runtimeConfigs` | 特殊配置 | `/runtime-configs` | 渠道/端/版本行为 | 表+JSON 表单 | 只允许已锁 payload 键 |
| `announcements` | 公告 | `/announcements` | 定向投放 | 表+抽屉 | 端/渠道多选；版本范围日期码 |
| `inbox` | 站内信 | `/inbox` | 发给一个人 | 表单 userId+标题正文 | 禁止全站群发 |
| `chatSuggestions` | 推荐问题 | `/chat-suggestions` | 配新对话空态问题 | 表+抽屉 | 上架最多 12；下架不物理删 |
| `agentPrompts` | 提示词 | `/agent-prompts` | 改八条固定提示词 | 左 key 右编辑器 | 不能增删 key；保存后下一轮对话生效 |
| `llmModels` | 模型分流 | `/llm-models` | 多个模型与权重 | 表+抽屉 | 密钥只写不回显；列表只显示末 4 位 |
| `kb` | 知识库 | `/kb` | 规则正文 | 表+编辑器 | retired 不停物理删若仍检索 |
| `godView` | 用户视角 | `/god-view` | 签发 H5 看某账号页面 | userId + 复制 token/打开 H5 | 菜单名「用户视角」；也可从 `userDetail` 打开；prod 默认不可用 |
| `audit` | 审计 | `/audit` | 追操作 | 表 | 无密钥 |

### 3.2 海报编辑（重点）

- 上传模板原图；叠加半透明矩形，拖改 `qrX/Y/W/H`，右侧同步数字。
- 预览：用占位邀请 URL 在 **浏览器 Canvas** 贴码，与消费者端算法一致。
- 生效：`el-date-picker` 日期时间范围，可清空。列表用 Tag 显示「未开始 / 生效中 / 已过期 / 已下架」。

### 3.3 版本发布（重点）

- `versionCode` 占位符 `20261029`，校验八位日期。
- Android：`channel` 必填下拉（Nacos 白名单）。iOS 锁 `appstore`，H5 锁 `web`。
- 同一天同渠道已有行：表单提示改这一行，不要再新建。
- `force_update` 开关；`published` 后才出现在检查更新里。

### 3.4 用户详情（重点）

壳：顶栏固定该用户 `nickname` / `id` / 状态；**左侧二级菜单 = 用户视角**（对齐消费者「我的」信息架构，不是运营全局表）。右区只带当前 `userId` 拉数。禁止展示 JWT、联盟 PID、完整收款账号。

| 菜单 ID | 名称 | 右区 | 数据 |
| --- | --- | --- | --- |
| `udOverview` | 资料 | 描述列表 | `app_user` 运营可见全列：id、昵称、头像、**完整手机**、状态、locale、邀请码、**邀请人 id**、推送开关、注册/更新/软删时间 |
| `udIdentity` | 登录绑定 | 表 | `app_user_identity`：provider、渠道 uid、unionId、绑定时间 |
| `udPref` | 偏好与设备 | 两块 | 长期偏好；已装电商 APP 白名单快照 |
| `udChat` | 对话 | 会话表 | 该用户会话；点进既有会话详情 |
| `udOrders` | 订单 | 订单表 | 该用户电商单；点进既有订单详情 |
| `udGoods` | 浏览/收藏/合集 | Tab 三表 | 只读，不替用户删合集商品 |
| `udWallet` | 返佣钱包 | 余额+流水 | 金额两位；提现只 `accountMask` |
| `udCredits` | 对话积分 | 余额+流水+积分单 | 调账走积分流水页并带 userId |
| `udInvite` | 邀请 | 统计+上下级 | 本人码、上级、有效下级人数 |
| `udAuth` | 联盟授权 | 四行 | 授权状态；无 PID / relationId |
| `udTickets` | 工单 | 表 | 该用户工单 |
| `udInbox` | 站内信 | 表+发信 | 收件列表；发信预填 userId |
| `udLive` | 打开用户视角 | 按钮 | 签发短时 JWT，新开 H5（顶栏警告条）。`prod` 未开则禁用 |

深链：`/users/:id` 默认资料。子菜单用 query `tab=` 或子路径均可，刷新不丢用户。列表筛：手机精确、邀请码、userId、昵称模糊、状态。

---

## 4. 主路径

运营登录（SSO）→ 工作台 → **用户列表 → 用户详情（用户视角菜单看全量）**。发版本 / 配渠道行为 / 发公告或私信。海报：上传 → 框二维码 → 看 Canvas 预览 → 设生效期 → 上架。用户视角：详情内打开，或左栏「用户视角」填 userId → 打开 H5（顶栏警告条）。
