# 管理后台权限

本文是管理后台角色、权限点和鉴权方式的唯一权威。菜单有哪些仍以 [`admin-ui.md`](./admin-ui.md) 为准，接口有哪些仍以 [`api-admin.md`](./api-admin.md) 为准。本文只规定谁能看见、谁能改、缺了权限时前后端怎么表现。

不在本仓库建角色表、权限表或管理员账号。角色和权限点加在 **omino-sso 里这个后台对应的应用**上。平台角色 `users.roles`（例如 `admin`）不自动拥有本后台的任何权限。应用管理员（`client_members.is_admin`）只能在门户里给人分角色，自己没有本后台权限点时，登录后同样看不到菜单。

一人可以有多个应用内角色。权限取这些角色的并集。SSO 的 userinfo 已经按这个规则去重。

---

## 1. 登录后拿到什么

`app_roles` 和 `permissions` 只在 `GET {SSO_ISSUER}/oauth2/userinfo` 里，**不在 id_token 里**。字段含义以 omino-sso 的 userinfo 契约为准：

| 字段 | 含义 | 本后台怎么用 |
| --- | --- | --- |
| `roles` | 平台角色 | 忽略。不能用来开菜单 |
| `app_roles` | 该用户在本应用内的角色名 | 展示，以及和权限点一起交给前端 |
| `permissions` | 这些角色绑过的权限编码，去重 | 菜单、按钮、接口鉴权都看这个数组 |

父级权限不会自动带上子级。SSO 只返回角色上真正绑过的编码。所以分组节点不要绑到角色，只绑下面的叶子编码。

登录成功时，Java 用 access token 再请求一次 userinfo，把 `name`、`app_roles`、`permissions` 放进服务端会话。浏览器仍然只有 `sid_admin`，会话里的 access token 不下发。

之后每个已登录请求，若距离上次拉取超过 60 秒，用会话里的 access token 再拉一次 userinfo，更新角色和权限点。拉取失败或令牌失效：作废本应用会话，按现有未登录规则返回 `401` / `40100`。这样在 SSO 里改角色或踢出成员，最多约 1 分钟后生效，不必等重新登录。

`GET /api/admin/v1/auth/me` 读会话，不把 access token 放进响应：

```json
{
  "sub": "01J...",
  "name": "张三",
  "appRoles": ["support"],
  "permissions": ["dash:read", "user:read", "user:phone"]
}
```

已登录但一个权限点都没有：HTTP `200`，两个数组都是 `[]`。前端留顶栏（姓名、主题、登出），主区写「还没有后台权限，请联系应用管理员」。业务接口返回 `403` / `40300`，`message` 为「没有权限」。需要的编码只打在服务端日志里，不放进 `message`。

---

## 2. 角色

角色名只能是字母和数字，最长 32，与 SSO 的 `client_roles.name` 一致。

| 角色 | 名称 | 给谁 | 边界 |
| --- | --- | --- | --- |
| `owner` | 负责人 | 能对本后台所有功能负责的人 | 全部叶子权限，含用户视角、打款、调账、发版、模型密钥 |
| `ops` | 运营 | 日常内容和用户服务 | 能处理用户、工单、公告、私信、海报、推荐问题。不能打款、调账、发版、改提示词、改模型、开用户视角 |
| `support` | 客服 | 答疑和工单 | 能看用户和完整手机、会话、订单状态、该用户的钱包和积分余额，能回复和改工单。不能看公司留存，不能改配置 |
| `finance` | 财务 | 对账和出款 | 能看三本账和佣金两列，能打款、拒绝提现、改套餐、调账、标模型单价。不能看完整手机和会话正文，不能开用户视角 |
| `growth` | 增长 | 投放和素材 | 能看漏斗和用户列表（手机打码），能改海报、公告、推荐问题。不能发私信、不能发版 |
| `analyst` | 分析 | 只看数的人 | 工作台、漏斗、三本账、订单、钱包、积分、提现列表、审计。没有任何写操作，不能打款，不能标单价 |
| `engineer` | 工程 | 对话和同步排障 | 能改提示词、模型分流和密钥、知识库、推荐问题，能看会话和订单状态，能改同步水位并重试同步。不能看资金页、完整手机，不能打款和调账 |
| `releaser` | 发版 | 客户端开发 | 能看版本和特殊配置，能发布渠道包、改版本说明、打开强制更新。不能改特殊配置，也没有其他菜单 |

不要把「只看数」的人放进 `finance`。`finance` 带打款。客户端开发发版用 `releaser`，不要为此把 `release:write` 加进 `engineer`。

---

## 3. 权限点

编码匹配 SSO 的 `^[A-Za-z0-9_.:-]{1,64}$`。动作用冒号隔开。`read` 是进入菜单和调用查询。写操作单独一个编码，没有读权限时也不给写权限。

分组编码只为了在 SSO 的权限树里折叠，应用不检查它们。

### 3.1 分组（不要绑到角色）

| code | 名称 | sort |
| --- | --- | --- |
| `group:overview` | 概览 | 10 |
| `group:trade` | 交易 | 20 |
| `group:people` | 用户 | 30 |
| `group:asset` | 资产 | 40 |
| `group:growth` | 增长 | 50 |
| `group:ops` | 运营 | 60 |
| `group:chat` | 对话 | 70 |
| `group:content` | 内容 | 80 |
| `group:system` | 系统 | 90 |

### 3.2 叶子

父级是上面的分组。`sort` 在同一父级里递增。

| code | 名称 | 父级 | 菜单或按钮 | 允许的操作 |
| --- | --- | --- | --- | --- |
| `dash:read` | 看工作台 | `group:overview` | 工作台 `/` | `GET /v1/dashboard`；`GET /v1/stats/overview`。没有 `stats:money` 时响应里去掉 `platform_retain_settled` 和 `wallet_liability` |
| `stats:funnel` | 看漏斗 | `group:overview` | 漏斗 | `GET /v1/stats/funnel`。没有 `stats:money` 时 `commissionRate` 为 `null` |
| `stats:money` | 看资金 | `group:overview` | 三本账、风险 | `GET /v1/stats/ledger`、`GET /v1/stats/risk`、`GET /v1/stats/model-prices`。同时解开工作台里那两个金额，以及订单上的媒体返佣列 |
| `stats:price` | 标模型单价 | `group:overview` | 三本账里的单价抽屉 | `PUT /v1/stats/model-prices/{modelName}`。没有 `stats:money` 时抽屉也不出现 |
| `order:read` | 看订单 | `group:trade` | 电商订单 | `GET /v1/orders`、`GET /v1/orders/{id}`。没有 `stats:money` 时去掉 `mediaCommission`、`parentUserId`、`walletLedgerId` |
| `sync:read` | 看同步 | `group:trade` | 同步游标 | `GET /v1/order-sync` |
| `sync:retry` | 重试同步 | `group:trade` | 重试按钮 | `POST /v1/order-sync/{platform}/retry` |
| `sync:write` | 改水位 | `group:trade` | 设置下单水位和更新水位 | `PATCH /v1/order-sync/{platform}` |
| `user:read` | 看用户 | `group:people` | 用户列表、用户详情 | `GET /v1/users`、`GET /v1/users/{id}`。列表手机保持打码 |
| `user:phone` | 看完整手机 | `group:people` | 详情里的手机号 | 详情接口的手机字段。没有本权限时详情也打码，和列表同一规则 |
| `user:live` | 打开用户视角 | `group:people` | 用户视角 | `POST /v1/god-view/tokens`。还要 `GOD_VIEW_ENABLED=true`，否则仍是 `40303` |
| `chat:read` | 看会话 | `group:people` | 会话观测 | `GET /v1/conversations`、`GET /v1/conversations/{id}/messages` |
| `ticket:read` | 看工单 | `group:people` | 工单 | `GET /v1/tickets`、`GET /v1/tickets/{id}` |
| `ticket:reply` | 回复工单 | `group:people` | 回复框 | `POST /v1/tickets/{id}/reply` |
| `ticket:update` | 改工单 | `group:people` | 状态、优先级、指派 | `PATCH /v1/tickets/{id}` |
| `wallet:read` | 看钱包 | `group:asset` | 用户详情里的返佣钱包 | `GET /v1/wallet-ledger`。不单开左栏菜单 |
| `withdraw:read` | 看提现 | `group:asset` | 提现审核 | `GET /v1/withdrawals`。收款账号仍只有 `accountMask` |
| `withdraw:pay` | 处理提现 | `group:asset` | 打款、拒绝 | `POST /v1/withdrawals/{id}/payout`、`POST /v1/withdrawals/{id}/reject` |
| `credit:read` | 看积分 | `group:asset` | 套餐、积分订单、积分流水，以及用户详情里的积分 | 对应的 GET，含 `GET /v1/credit-rate` |
| `credit:write` | 改套餐 | `group:asset` | 套餐的新建和上下架 | `POST`、`PATCH /v1/credit-packages` |
| `credit:adjust` | 积分调账 | `group:asset` | 流水页的调账 | `POST /v1/credits/adjust`。备注仍必填 |
| `poster:read` | 看海报 | `group:growth` | 邀请海报 | `GET /v1/invite-posters` |
| `poster:write` | 改海报 | `group:growth` | 上传、改矩形、上下架 | `POST`、`PATCH` |
| `release:read` | 看版本 | `group:ops` | 版本发布、特殊配置 | 两个资源的 GET |
| `release:write` | 发版本 | `group:ops` | 版本页的发布、改说明、强制更新 | `POST`、`PATCH /v1/releases` |
| `config:write` | 改特殊配置 | `group:ops` | 特殊配置的新建和修改 | `POST`、`PATCH /v1/runtime-configs`。没有 `release:read` 时不显示这个菜单 |
| `announce:read` | 看公告 | `group:ops` | 公告 | GET |
| `announce:write` | 发公告 | `group:ops` | 新建和修改 | POST、PATCH |
| `inbox:send` | 站内信 | `group:ops` | 站内信，以及用户详情里的站内信 | `GET`、`POST /v1/inbox` |
| `suggest:read` | 看推荐问题 | `group:chat` | 推荐问题 | GET |
| `suggest:write` | 改推荐问题 | `group:chat` | 新建和上下架 | POST、PATCH |
| `prompt:read` | 看提示词 | `group:chat` | 提示词 | GET |
| `prompt:write` | 改提示词 | `group:chat` | 保存正文 | PATCH |
| `model:read` | 看模型 | `group:chat` | 模型分流 | GET。响应仍然只有末 4 位掩码 |
| `model:write` | 改模型 | `group:chat` | 新建、改权重、改密钥 | POST、PATCH |
| `kb:read` | 看知识库 | `group:content` | 知识库 | GET |
| `kb:write` | 改知识库 | `group:content` | 新建和修改 | POST、PATCH |
| `audit:read` | 看审计 | `group:system` | 审计 | `GET /v1/audit-logs` |

登录、回调、登出、`/auth/me`、健康检查、OpenAPI 不挂权限点。未登录仍是 `40100`。

用户详情里的页签：

| 页签 | 权限 |
| --- | --- |
| 资料、登录绑定、偏好与设备、浏览/收藏/合集、邀请、联盟授权 | `user:read` |
| 资料中的完整手机 | 另外要 `user:phone` |
| 对话 | `chat:read` |
| 订单 | `order:read` |
| 返佣钱包 | `wallet:read` |
| 对话积分 | `credit:read` |
| 工单 | `ticket:read` |
| 站内信 | `inbox:send` |
| 打开用户视角 | `user:live` |

没有某个页签的权限时，不显示该页签，也不要为它发请求。直接打开带 `tab` 的地址时，停在资料页。

---

## 4. 每个角色绑哪些叶子

下表是建议的初始绑定。之后在 SSO 里改绑定即可，不用改代码，只要编码还在第 3 节里。

| 权限 | owner | ops | support | finance | growth | analyst | engineer | releaser |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `dash:read` | 有 | 有 | 有 | 有 | 有 | 有 | 有 | |
| `stats:funnel` | 有 | 有 | | 有 | 有 | 有 | 有 | |
| `stats:money` | 有 | | | 有 | | 有 | | |
| `stats:price` | 有 | | | 有 | | | | |
| `order:read` | 有 | 有 | 有 | 有 | | 有 | 有 | |
| `sync:read` | 有 | | | 有 | | | 有 | |
| `sync:retry` | 有 | | | | | | 有 | |
| `sync:write` | 有 | | | | | | 有 | |
| `user:read` | 有 | 有 | 有 | | 有 | | | |
| `user:phone` | 有 | 有 | 有 | | | | | |
| `user:live` | 有 | | | | | | | |
| `chat:read` | 有 | 有 | 有 | | | | 有 | |
| `ticket:read` | 有 | 有 | 有 | | | | 有 | |
| `ticket:reply` | 有 | 有 | 有 | | | | | |
| `ticket:update` | 有 | 有 | 有 | | | | | |
| `wallet:read` | 有 | | 有 | 有 | | 有 | | |
| `withdraw:read` | 有 | | | 有 | | 有 | | |
| `withdraw:pay` | 有 | | | 有 | | | | |
| `credit:read` | 有 | 有 | 有 | 有 | | 有 | | |
| `credit:write` | 有 | | | 有 | | | | |
| `credit:adjust` | 有 | | | 有 | | | | |
| `poster:read` | 有 | 有 | | | 有 | | | |
| `poster:write` | 有 | 有 | | | 有 | | | |
| `release:read` | 有 | 有 | | | 有 | | | 有 |
| `release:write` | 有 | | | | | | | 有 |
| `config:write` | 有 | | | | | | | |
| `announce:read` | 有 | 有 | | | 有 | | | |
| `announce:write` | 有 | 有 | | | 有 | | | |
| `inbox:send` | 有 | 有 | | | | | | |
| `suggest:read` | 有 | 有 | | | 有 | | 有 | |
| `suggest:write` | 有 | 有 | | | 有 | | 有 | |
| `prompt:read` | 有 | | | | | | 有 | |
| `prompt:write` | 有 | | | | | | 有 | |
| `model:read` | 有 | | | | | | 有 | |
| `model:write` | 有 | | | | | | 有 | |
| `kb:read` | 有 | 有 | 有 | | | | 有 | |
| `kb:write` | 有 | 有 | | | | | 有 | |
| `audit:read` | 有 | | | 有 | | 有 | 有 | |

客服能看钱包和积分，是为了回答「返佣到了没有、积分还有多少」。客服看不到媒体返佣和三本账。

运营能看订单和用户佣金相关的订单状态，看不到媒体返佣、上级 id、钱包流水 id。这三列只在同时具备 `order:read` 和 `stats:money` 时出现。

---

## 5. 前端

登录后的壳先请求 `/api/admin/v1/auth/me`，把 `appRoles` 和 `permissions` 放在内存里。刷新页面重新要一次。不要写进 `localStorage`。

左栏一项是否显示，看第 3.2 节该菜单对应的那个读权限。一组下面一项都没有时，整组不显示。收起后的悬停文字也只包含还显示的项。

路由：

- 目标路由需要的权限不在数组里：进 `/forbidden`，文案「没有权限」，留顶栏。
- 登录后若没有 `dash:read`，不要留在 `/`。打开该用户第一项还看得到的菜单。一项都没有则用第 1 节的空状态。
- 按钮和抽屉按第 3.2 节的写权限隐藏。隐藏不够，接口仍会拒绝。

深色浅色、登出不看权限。

---

## 6. 后端

会话里已经有权限数组。业务接口在进入 Controller 之前核对，不在每个 Service 里散落判断。

- 没有会话：保持 `401` / `40100`。
- 有会话但缺权限：`403` / `40300`，`message` 为「没有权限」。
- 路径与所需编码的对照就是第 3.2 节。一个路径只要求一个编码。列裁剪是额外规则，不是第二个编码。
- `user:live` 通过之后，上帝视角开关仍按现有 `40303`。
- 未知的权限编码忽略。有人在 SSO 里多建了一个编码，不会打开本后台没有的功能。
- 本仓库不缓存权限点目录。目录就是本文。代码里用常量列出第 3.2 节的叶子，防止打错字。

本地还没在 SSO 里建好角色时，只有 `APP_ENV=local` 且 `ADMIN_PERMISSION_OPEN=true` 才跳过校验，并在 `/auth/me` 里返回全部叶子，`appRoles` 为 `["owner"]`。`test` 和 `prod` 忽略这个变量，一律校验。example 里默认写 `false`。

---

## 7. 加到 SSO 时怎么做

在门户里打开本后台这个应用（应用管理员），不要加到平台角色上。

1. 按第 3.1 节建 9 个分组，父级为空。
2. 按第 3.2 节建叶子，父级选对应分组。名称用表里的中文。
3. 建 8 个角色，名称用第 2 节的英文名，描述用表里的「给谁」。客户端开发赋 `releaser`。
4. 按第 4 节把叶子绑到角色。不要绑 `group:*`。
5. 把成员加入应用，再赋角色。只加入应用、不赋角色的人，登录后两个数组都是空的。

改绑定后不用发版。已登录的人在下一分钟的 userinfo 刷新里拿到新权限。

本后台不做「角色管理」页。分人、建角色、改绑定都在 SSO。
