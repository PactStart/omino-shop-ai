# 管理端会话

实现前把本文件的路径原样写入 [`docs/api-admin.md`](../../../docs/api-admin.md)。在那次文档变更之前不写控制器。

浏览器只访问 Admin 进程。授权码在 Java 内交换。响应与日志不出现 access_token、refresh_token、id_token。

Cookie 名 `sid_admin`。`HttpOnly`、`SameSite=Lax`。生产 `Secure`。不要用 `sid`，避免和 omino-sso 在同一主机上的会话互相覆盖。令牌只存在服务端会话里。

IdP 注册的回调必须与 `SSO_REDIRECT_URI` 完全一致，包含上下文路径，例如 `http://127.0.0.1:8182/api/admin/v1/auth/callback`。

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| GET | `/api/admin/v1/auth/login` | 无会话时 302 到 omino-sso 授权端点。PKCE 仅 S256。已有会话则 302 到登录后的后台入口 |
| GET | `/api/admin/v1/auth/callback` | 校验 state，用授权码换令牌，建立 `sid_admin`。失败不发会话 cookie |
| POST | `/api/admin/v1/auth/logout` | 清本地会话。再引导浏览器到 IdP `/logout`，清 IdP 会话。不调用会留下 IdP 会话的 `end_session` 作为唯一步骤 |

未登录访问 `/api/admin/v1/**` 业务路径：HTTP `401`，信封 `code` 为 `40100`，`data` 为 null。`GET /api/admin/actuator/health` 保持现有健康正文，不套信封，不要求会话。

不建管理员密码表。不提供账号密码登录。
