# 第 5 层：工单、站内信与设置

## 生成范围

页面：`tickets`、`ticketDetail`、`inbox`、`settings`、`settingsDisplay`、`settingsPush`、`settingsAbout`；Overlay：logoutConfirm、deleteConfirm、toast。

## tickets `/me/tickets`

结构：TopBar → 状态筛选 → TicketRow 列表。页面不提供新建按钮，工单只能由对话创建。

当前 [`app-api-catalog.md`](../../app-api-catalog.md) 明确没有消费者工单列表接口，因此列表字段、状态筛选值和分页均标 `API pending`。可以生成信息架构稿，不能标 `Ready for dev`，不能仿照 Admin API 虚构消费者接口。

## ticketDetail `/me/tickets/:id`

结构：状态头 → 工单元信息 → 时间线 → 未关闭时的证据 Composer。回复只表示新增沟通，不表示已修复。接口与字段仍为 `API pending`；补图流程需等待上传和工单接口定义。

建议状态：loading、open、waiting、closed、evidence-uploading、error，但名称必须在接口确定后复核。

## inbox `/me/inbox`

列表显示 title、去标签的正文摘要、readAt 状态和 createdAt；未读行有克制的视觉标记。点击调用 read 并打开详情表现。

- 接口：GET inbox、POST read；“我的”红点用 unread-count。
- `format=plain` 纯文本；`format=html` 只渲染白名单 HTML。
- 状态：loading、empty、default、marking-read、pagination-loading、error、detail-plain、detail-html。
- 当前路由只定义 inbox 页面，若详情采用新路由需先更新权威 UI；设计基线可用同页 Sheet/展开态表达。

## settings `/me/settings`

分组顺序：显示（风格、字体）→ 商品（浏览、收藏）→ 关于（协议、隐私、关于、检查更新）→ 通知（推送）→ 账号（注销、退出）。页面无 Tab。

注销是线框红字并需要输入 `DELETE`；退出登录需要确认。H5 `gv=1` 禁用注销生产账号并解释原因。

## settingsDisplay `/me/settings/display`

ThemePicker 显示 V1 晴空绿、V2 科技黑、V3 简约白、V4 咸鱼黄、V5 京东红。字体为 sm/md/lg。两项都只存本地，选择后当前及已打开界面立即刷新。布局包含真实组件预览，而不是装饰性主题海报。非法或空主题回落 V1。

## settingsPush `/me/settings/push`

单一 Switch，初值来自 `GET /me`，修改走 `PATCH /me/push`。设计 saving、success、error-reverted；失败时恢复原值，不能只 Toast 而保持错误视觉状态。

## settingsAbout `/me/settings/about`

显示当前版本、用户协议、隐私政策、关于和检查更新。协议 URL 来自 `GET /legal`；更新走 `GET /app/check-update`。action=optional 打开可选更新 Sheet，force 进入阻断页，none 用 Toast 表达已是最新。

## Overlay 与反馈

- `logoutConfirm`：取消/退出；成功后进入 login。
- `deleteConfirm`：风险说明、输入 DELETE、取消/注销；调用 `POST /me/delete`。
- `toast`：成功或失败的一句话，底中约 2 秒；不承载不可逆确认。

## Figma 输出清单

将 `API pending` 工单稿与可开发的 inbox/settings 分到不同 Section。先生成设置列表、主题选择、推送 Switch、危险确认组件，再完成页面和更新检查原型。

