# 第 5 层：认证、启动与系统页面

## 生成范围

页面：`splash`、`login`、`loginPhone`、`forceUpdate`；Overlay：`optionalUpdate`、`announcementSheet`。默认生成 H5 `390 x 844`、V1、字号 md，再适配原生安全区。

上游约束已浓缩在本文；详细字段以 [`app-api-catalog.md`](../../app-api-catalog.md) 的登录和客户端头为准，组件用法见 [`component-library.md`](../03-components/component-library.md)。

## splash `/splash`

- 目标：最短时间完成会话恢复、更新检查、公告处理与登录分流。
- 布局：全屏画布，品牌字标居中偏上；底部仅允许版本/加载状态弱信息。
- 状态：launching、checking-update、restoring-session、offline、routing。
- 禁止：功能轮播、营销卡片、登录按钮闪现、全屏 Lottie。
- 输入：可选 `inviteCode`；必须跨分流保留。

## login `/login`

- 布局：安全区 → 品牌/一句短标题 → 可伸缩留白 → 登录入口按钮栈 → 协议链接。
- Demo 只显示手机号主入口；微信、Apple、Google 隐藏而不是 disabled。
- 不提供邮箱密码或 SSO。协议 URL 来自 `GET /legal`，加载失败时不能写死正文。
- 状态：default、legal-loading、legal-error、invite-present。

## loginPhone `/login/phone`

- 布局：返回 TopBar → 手机号字段 → 验证码输入 → 获取/倒计时 → 主按钮 → 就地错误。
- 交互：手机号 11 位；发送验证码后 60 秒倒计时；登录中按钮 loading 且防重复提交。
- 接口：`POST /auth/sms/send`、`POST /auth/sms/login`；登录 body 可带 `inviteCode`。
- 状态画板：phone-empty、phone-valid、otp-sent、countdown、submitting、code-error、rate-limited、network-error。

## forceUpdate `/update`

- 布局：不可返回的全屏页面；版本标题、版本号、更新说明、唯一主按钮。
- `format=plain` 显示纯文本；`format=html` 只表现白名单 HTML 的排版能力。
- 下载 URL 为空时显示明确错误与重试，不允许进入业务页。
- 状态：content、download-starting、download-error。

## optionalUpdate

底部 Sheet，包含标题、版本、更新说明、“稍后”和“更新”。关闭后继续启动流程。不能与强制更新共用可关闭状态。

## announcementSheet

底部 Sheet；支持多条滑动和页码，已读只存本地。内容过长时 Sheet 内滚动，主关闭动作稳定。设计 plain、html、single、multiple、content-error。

## Figma 输出清单

1. Section `Auth System / Wireframe`：上述页面主要状态。
2. Section `Auth System / V1`：通过的状态高保真。
3. Components：OTP、LegalLinks、UpdateContent、AnnouncementPager。
4. Prototype：启动至登录、可选更新继续、强制更新阻断。

