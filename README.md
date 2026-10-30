# omino-shop-ai

导购对话。消费者在 Android、iOS 或 H5 里说话，Java 开一轮对话并把事件泵出去；商品下单仍在淘宝、京东、拼多多、唯品会自己的 APP。管理后台只认 [omino-sso](https://github.com/PactStart/omino-sso) 的授权码加 PKCE。

一套 Java 代码，两个进程：`/api/app` 与 `/api/admin`。字段和接口以 [`AGENTS.md`](AGENTS.md) 为准，本文件只说明当前进度和怎么在本机跑起来。

## 现在做到哪

feat 2（`002-java-api`）已落地：APP、Admin 和 `/internal/v1` 的 HTTP、业务表、订单同步可以跑，并用测试验收。页面和 Agent 业务还没做。

| 部分 | 地址 | 状态 |
| --- | --- | --- |
| APP API | `:8081`，上下文 `/api/app` | 短信登录、开一轮对话、商品详情与转链门闩、订单同步与返佣、提现、积分、邀请海报数据、工单、版本公告、内部工具 |
| Admin API | `:8082`，上下文 `/api/admin` | 运营接口；登录走 omino-sso，授权码 + PKCE S256 |
| 接口文档 | `/api/app/swagger-ui.html`、`/api/admin/swagger-ui.html` | 不要求登录 |
| Agent | `127.0.0.1:8090` | 只有 `GET /health` |
| 管理后台 | `:5173` | Vite 空壳 |
| 官网 / 落地页 / H5 | `:5175` / `:5176` / `:5177` | Vite 空壳 |
| Android / iOS | 包名 `com.omino.shopai` | 工程能打开，页面未做 |

契约在 [`docs/api-app.md`](docs/api-app.md)、[`docs/api-admin.md`](docs/api-admin.md)、[`docs/api-internal.md`](docs/api-internal.md)、[`docs/data-model.md`](docs/data-model.md)、[`docs/conversation.md`](docs/conversation.md)。后面的页面和 Agent 按 [`docs/speckit-phases.md`](docs/speckit-phases.md) 的 feat 3 起做。

## 本地启动

需要 Docker、JDK 21、Maven、uv（Python 3.12）、Node。工作目录是仓库根。

```bash
make env    # 没有 deploy/env/local.env 时从 example 复制
make up     # Postgres+pgvector、Redis、Nacos、Langfuse
make app    # APP API
make admin  # 另一个终端再起 Admin API
```

`make app` 和 `make admin` 会把 `deploy/env/local.env` 导入进程。yml 里的默认端口是 `5432` 和 `6379`。本机这两个端口若已被别的项目占用，只改 `local.env` 里的 `PG_PORT`、`REDIS_PORT`。不经过 make、也不导出这份文件就直接跑 `mvn`，会连到默认端口上的别的数据库。

其余目标看 `make help`：Agent、四个前端空壳、Android debug 包、用 Xcode 打开 iOS 工程、`make test`。

健康检查：

- `GET http://127.0.0.1:8081/api/app/actuator/health`
- `GET http://127.0.0.1:8082/api/admin/actuator/health`
- `GET http://127.0.0.1:8090/health`

## 边界

不做自营购物车，不做美团，不做 Flutter。联盟密钥和转链只在 Java。积分不能提现，也不能转回钱包。管理后台不建自己的密码表。
