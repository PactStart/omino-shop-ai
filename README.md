# omino-shop-ai

面向消费者的 AI 购物助手。用文字说出需求，由 Agent 跨淘宝、京东、拼多多、唯品会推荐商品；下单仍在各电商自己的 APP。管理后台只认 [omino-sso](https://github.com/PactStart/omino-sso) 的授权码 + PKCE（仅 S256）。

- 一套 Java，两个进程：`/api/app` 与 `/api/admin`。`/internal/v1` 只挂在 APP 进程，给 Agent 调
- 对话入口是 Android / iOS / H5 → Java SSE → Agent。Agent 只监听本机，不对 APP 直连
- 实现细节以 `[AGENTS.md](AGENTS.md)` 为权威。消费者接口见 `[docs/api-app.md](docs/api-app.md)`，运营接口见 `[docs/api-admin.md](docs/api-admin.md)`，Tool 见 `[docs/api-internal.md](docs/api-internal.md)`，表见 `[docs/data-model.md](docs/data-model.md)`，对话与 SSE 见 `[docs/conversation.md](docs/conversation.md)`。页面见 `[docs/app-ui.md](docs/app-ui.md)`、`[docs/admin-ui.md](docs/admin-ui.md)`。阶段划分见 `[docs/speckit-phases.md](docs/speckit-phases.md)`

## 1. 功能


| 模块                   | 能力                                                      | 现状                               |
| -------------------- | ------------------------------------------------------- | -------------------------------- |
| APP API              | 短信登录、开一轮对话、商品详情与转链门闩、订单同步与返佣、提现、积分、邀请海报数据、工单、版本公告、内部工具  | 可调用。联盟官方 SDK 未接，无密钥时上游返回 `50010` |
| Admin API            | 运营接口；登录走 omino-sso                                      | 可调用                              |
| 接口文档                 | `/api/app/swagger-ui.html`、`/api/admin/swagger-ui.html` | 不要求登录                            |
| Agent                | 意图、搜品、授权卡片                                              | 只有 `GET /health`                 |
| 管理后台 / 官网 / 落地页 / H5 | 按 `admin-ui.md`、`app-ui.md` 的页面                         | Vite 空壳                          |
| Android / iOS        | 包名 `com.omino.shopai`                                   | 工程能打开，页面未做                       |


**非目标（本阶段不做）**：自营购物车、美团、Flutter、积分提现或转回钱包、管理后台自建密码表、服务端合成邀请海报。

## 2. 技术栈


| 层             | 组件                                                        |
| ------------- | --------------------------------------------------------- |
| 语言 / 框架       | JDK 21、Spring Boot 3.4.11、MyBatis-Plus 3.5.9              |
| 存储            | PostgreSQL 16 + pgvector、Redis 7.4                        |
| Agent         | Python 3.12、FastAPI 0.115.6、LangGraph（业务图未接）              |
| 管理后台          | Vue 3.5.13、Vite 6.3.5、TypeScript 5.7.2、Element Plus 2.9.1 |
| 官网 / 落地页 / H5 | 同族 Vue，不用 Element Plus。H5 用 Tailwind 4.1.0                |


锁定版本与「用哪部分」以 `AGENTS.md` §2 为准。

## 3. 端口与路径

本地端口避开同机的 omino-sso（IdP `8080`、管理 API `8081`、门户 `5173`、后台 `5174`、一体化后台容器 `8090`、Redis 宿主机 `6380`、MySQL `3307`）。


| 服务          | 地址                      | 说明                                                |
| ----------- | ----------------------- | ------------------------------------------------- |
| APP API     | `http://127.0.0.1:8181` | 上下文 `/api/app`                                    |
| Admin API   | `http://127.0.0.1:8082` | 上下文 `/api/admin`                                  |
| Agent       | `http://127.0.0.1:8190` | 只监听本机                                             |
| 管理后台（dev）   | `http://localhost:5183` | `apps/admin-web`                                  |
| 官网（dev）     | `http://localhost:5175` | `apps/www`                                        |
| 落地页（dev）    | `http://localhost:5176` | `apps/land`                                       |
| 消费者 H5（dev） | `http://localhost:5177` | `apps/h5`                                         |
| PostgreSQL  | `127.0.0.1:5432`        | 容器内仍是 5432。本机被占用时只改 `local.infra.env` 的 `PG_PORT` |
| Redis       | `127.0.0.1:6381`        | 容器内仍是 6379                                        |
| Nacos       | `127.0.0.1:8848`        | 另映射 `9848`。local 默认不连接                            |
| Langfuse    | `http://127.0.0.1:3100` | 容器内 3000                                          |


不要在 env 里设置 `SERVER_PORT`。APP 与 Admin 的端口写在各自的 `application.yml`。

## 4. 环境要求

- JDK 21、Maven 3.9
- Python 3.12 与 [uv](https://docs.astral.sh/uv/) 0.5
- Node.js 20+ / npm
- Docker 27+ 与 Docker Compose v2



## 5. 快速开始

工作目录是仓库根。

### 5.1 准备环境文件并启动依赖

```bash
make env          # 缺哪份 deploy/env/local.*.env 就从 example 复制，已有不覆盖
make up           # Postgres、Redis、Nacos、Langfuse
```

`make up` 只读 `deploy/env/local.infra.env`。密钥不进 Git。`PG_PASSWORD`、`LLM_CONFIG_KEY`、`JWT_ACCESS_SECRET`、`AGENT_INTERNAL_TOKEN` 只写在这一份里。

### 5.2 启动 Java

```bash
make app          # http://127.0.0.1:8181/api/app
make admin        # 另一个终端：http://127.0.0.1:8082/api/admin
```

两条命令都会先加载 `local.infra.env`，再加载 `local.app.env` 或 `local.admin.env`。不经过 make、也不导出这些文件就直接跑 `mvn`，会连到 yml 默认端口上的库。

Admin 的 `SSO_ISSUER` 指向本机 omino-sso 的 `http://127.0.0.1:8080`。回调必须与 IdP 里登记的完全一致，例如 `http://127.0.0.1:8082/api/admin/v1/auth/callback`。

### 5.3 启动 Agent

```bash
make agent        # http://127.0.0.1:8190/health
```

先加载 `local.infra.env`，再加载 `local.agent.env`。

### 5.4 启动前端

四个前端不读 `deploy/env`。目录里没有 `.env` 时，下面的命令会从该目录的 `.env.example` 复制。

```bash
make admin-web    # http://localhost:5183
make www          # http://localhost:5175
make land         # http://localhost:5176
make h5           # http://localhost:5177
```



## 6. 验证

```bash
curl -s http://127.0.0.1:8181/api/app/actuator/health
curl -s http://127.0.0.1:8082/api/admin/actuator/health
curl -s http://127.0.0.1:8190/health
```

接口文档：[http://127.0.0.1:8181/api/app/swagger-ui.html](http://127.0.0.1:8181/api/app/swagger-ui.html)、[http://127.0.0.1:8082/api/admin/swagger-ui.html](http://127.0.0.1:8082/api/admin/swagger-ui.html)。

`make test` 跑 Java 与 Agent 测试。

## 7. 构建

```bash
make test
make android      # apps/android 的 debug 包
make ios          # 用 Xcode 打开 apps/ios
```

其余目标看 `make help`。

## 8. 开发流程

一次只开一个功能，按 `[docs/speckit-phases.md](docs/speckit-phases.md)` 从 feat 3 往后做。改行为先改 `AGENTS.md` 或对应的 `docs/`，再改代码。工具怎么选见 `[docs/sdd.md](docs/sdd.md)`。

## 9. 目录速览

```
apps/admin-web     # 管理后台
apps/h5            # 消费者 H5
apps/www           # 官网
apps/land          # 邀请落地页
apps/android       # Kotlin + Compose
apps/ios           # SwiftUI
services/java      # shop-api-app（8181）与 shop-api-admin（8082）
services/agent     # 本机 8190
deploy/            # 本地编排与四份 env 示例
docs/              # 契约、数据模型、页面、阶段
specs/             # 已落地功能的规格
```



## 10. 常见问题


| 现象                             | 处理                                                                  |
| ------------------------------ | ------------------------------------------------------------------- |
| 启动报缺少 `deploy/env/local.*.env` | 先 `make env`                                                        |
| 连到了别的 Postgres，或密码认证失败         | 看 `local.infra.env` 的 `PG_PORT` / `PG_PASSWORD`，要和 `make up` 用的是同一份 |
| 端口被 omino-sso 占用               | 不要把 APP 改回 `8081`、Agent 改回 `8090`、管理后台改回 `5173`、Redis 改成 `6380`     |
| 直接 `mvn spring-boot:run` 连错库   | 用 `make app` / `make admin`，或先导出 `local.infra.env`                  |
| 搜品或详情返回 `50010`                | 联盟官方 SDK 还没接。密钥留空时适配器拒绝出站                                           |
| Admin 登录回调对不上                  | `SSO_REDIRECT_URI` 必须含 `/api/admin`，并事先在 omino-sso 登记               |


