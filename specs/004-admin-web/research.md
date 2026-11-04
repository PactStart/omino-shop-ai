# Research: 004-admin-web

未知项是：浏览器怎样登录且不碰令牌、缺的管理能力放在哪一层、同步重试怎样调用已有订单增量、海报二维码用什么画、分页类型放哪。字段和菜单不在这里重选。

## 登录仍由 Java 交换授权码

- Decision: 浏览器只打开 Admin API 的登录地址，授权码仍由现有 OIDC 客户端在 Java 里交换。成功后重定向到管理前端源（本地默认 `http://127.0.0.1:5183/`），不再停在 `/v1/users`。前端 axios `withCredentials: true`，开发时由 Vite 把 `/api/admin` 反代到 `127.0.0.1:8182`，页面和接口同一来源。不在浏览器里做 PKCE，不读 Cookie。
- Rationale: 契约要求 Java 作 BFF，Cookie 名已是 `sid_admin`。前端在 `5183`、接口在 `8182`，若浏览器跨端口直接调接口，Lax Cookie 不会带到另一来源。反代后登录跳转、会话和业务请求都走前端源。
- Alternatives considered:
  - 在 Vue 里用 `oidc-client-ts` 换 token：浏览器会持有 access_token，规格禁止。
  - 自建账号密码：宪章禁止。
  - 登录成功继续打开 `/v1/users` 的 JSON：运营看不到后台页面。

## 缺的能力补在 Admin 进程

- Decision: 页面规范已要求、而 `docs/api-admin.md` 还没有的读写，先补进该文档，再在 `shop-api-admin` 实现。契约里已有但进程没有的方法（推荐问题的列表和修改、订单详情、积分读与调账、海报与版本等的查询和更新）直接补实现，不改路径。不新增表。Admin 不 import `shop-api-app`。
- Rationale: 规格澄清覆盖了粘贴稿里的「不做新的后端接口」，范围只到页面已经点名的能力。表都在 common，Admin 用现有 Mapper 即可。
- Alternatives considered:
  - 空壳页：用户已否定。
  - 把缺的查询做进 APP 的 `/internal/v1` 再让页面去调：页面只能打 Admin API，内部工具白名单也不服务运营后台。

## 同步重试不把联盟调用搬进 Admin

- Decision: 运营点重试时，Admin 先写 `admin_audit_log`，再用已有内部令牌调用 APP 的 `POST /internal/v1/order-sync/{platform}`。该路径只执行现有 `OrderSyncService.sync`。平台只接受 `taobao`、`jd`、`pdd`、`vip`。这条路径不进入 Agent 工具白名单，不在 `docs/api-internal.md` 的 Tool 表里登记为模型可调用。
- Rationale: 订单增量和联盟 SDK 只在 APP 进程。Admin 若自己拉单，就要把 SDK 和入账事务复制一份，两个产出物还会互相 import。
- Alternatives considered:
  - Admin 直接依赖 `shop-api-app`：宪章禁止。
  - 只改游标、等下一次 Quartz：规格要求这次重试就再跑一次增量。
  - 新表记录重试请求：规格不新增表。

## 海报二维码

- Decision: 预览在浏览器 Canvas 上把占位邀请地址画成二维码，贴进模板矩形。引入 `qrcode`（MIT）前，先把精确版本写入 `AGENTS.md` §2.4，只用它的 Canvas 输出。不向 Admin API 要合成图。
- Rationale: 手写二维码编码违反「先检索再实现」。服务端合成被规格禁止。
- Alternatives considered:
  - 服务端预览接口返回 PNG：规格禁止，也会多一个成品图存储。
  - 页面上只画一个空矩形：验收要求能看见贴进去的码。

## 分页只有一个类型

- Decision: 把现有 `PageDto<T>` 从 `shop-api-app` 移到 `shop-api-common` 的 `com.omino.shopai.dto`。Admin 列表与 APP 列表都用它。字段仍是 `list`、`page`、`size`、`total`。
- Rationale: 权威目录要求全仓库只有这一个分页类型。它现在在 app 模块，Admin 不能引用。
- Alternatives considered:
  - Admin 再写一个同名字段的记录：两套分页。
  - 列表继续返回裸数组：契约已要求分页，用户详情也要按用户切页。

## 工单、知识库沿用已有枚举

- Decision: 工单状态只用已有 `open`、`processing`、`closed`。处理记录种类只用已有六种。知识库状态只用实体上的 `published`、`retired`。下架写成 `retired`，检索仍只读 `published`。回复只插 `reply`，不改状态。关单是一次 `status_change` 到 `closed`。
- Rationale: 枚举和「回复不等于关单」已经在数据和 APP 建单逻辑里。Admin 再造一套状态会和消费者端不一致。
- Alternatives considered:
  - 按数据模型注释把知识库写成 `active`：实体实际存的是 `published` / `retired`。实现前把 `docs/data-model.md` 的这一格改成与枚举一致，不改库里的取值。
