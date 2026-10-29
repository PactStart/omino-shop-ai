# Research: 001-scaffold

## Langfuse 服务端镜像

- Decision: 使用 `langfuse/langfuse:2`，容器名 `langfuse-web`，宿主机端口 `3100` 映射到容器 `3000`。
- Rationale: 权威文档锁定 Python 客户端 `langfuse` `2.57.0`，没有锁定服务端镜像。官方兼容矩阵里，Python SDK v2 在 Langfuse Server v2 上为 Full。官方 v2 部署指南推荐用主版本标签 `:2`，不要用 `:latest`。`AGENTS.md` §8 的编排命令只有 `langfuse-web` 与 `langfuse-db`，与 v2「一个 Web 容器 + 一个 Postgres」的形态一致。
- Alternatives considered:
  - `langfuse/langfuse:4`：不支持 Python SDK v2，会逼着改已锁定的客户端。
  - Server v3：仍支持 Python SDK v2，但官方自建还要 worker、ClickHouse、对象存储，超出 §8 已点名的两个服务。
  - 某个 2.x 补丁号：官方建议在主版本内跟随 `:2`。本功能不把补丁号再写进 `AGENTS.md` §2。以后若要换主版本，先改权威文档。

已知短板：Langfuse v2 的安全更新在 2025 年一季度结束。本功能只要求进程能启动并返回 HTTP 响应，不上报 trace。换到仍在维护的服务端主版本是后续独立变更，不在本功能里做。

## Langfuse 自用库

- Decision: 服务名 `langfuse-db`，镜像 `postgres:16-alpine`。与商品主库分开，不安装 pgvector，不存放业务表。
- Rationale: 规格要求 Langfuse 的库不是商品主库。v2 要求 Postgres 12 以上。主库镜像已锁定为 `pgvector/pgvector:pg16`，自用库用官方 Postgres 16，避免两套数据混在一个实例里。
- Alternatives considered: 在商品主库里再建一个 database。拒绝，因为备份、扩展和「不是商品主库」的验收会混在一起。

启动需要的本机占位值放在 `deploy/env/local.env.example`：`DATABASE_URL` 由编排注入，`NEXTAUTH_URL=http://127.0.0.1:3100`，以及 `NEXTAUTH_SECRET`、`SALT`、`ENCRYPTION_KEY`。`ENCRYPTION_KEY` 用 64 位十六进制占位，满足 v2 的长度要求。这些值只用于本机启动，不是生产密钥。

## Nacos 单机

- Decision: 镜像 `nacos/nacos-server:v2.4.3`，`MODE=standalone`，`NACOS_AUTH_ENABLE=false`，不配置 MySQL 数据源。HTTP 验收地址为 `http://127.0.0.1:8848/nacos/v1/console/health/liveness`。同时映射 `9848`，供以后的客户端使用，本功能不要求 Java 连接。
- Rationale: 官方镜像默认 `MODE=cluster`，单容器不改会起不来。`NACOS_AUTH_ENABLE` 默认 false；本地显式写 false，避免以后镜像默认值变化后变成必须登录。独立存储用嵌入式存储，不把 `SPRING_DATASOURCE_PLATFORM` 设为 mysql。规格禁止 MySQL，也禁止把 Nacos 当成服务发现。
- Alternatives considered: 打开鉴权并用示例账号登录。规格已澄清不要求登录，打开鉴权会把验收改成登录流程。

本地堆内存设为 `JVM_XMS=256m`、`JVM_XMX=256m`。官方默认 1g，本机空跑不需要。这只影响本地容器，不改变 Nacos 版本。

## Java 本功能不连接 Nacos

- Decision: 父 POM 的 `dependencyManagement` 导入已锁定的 Spring Cloud BOM 与 Spring Cloud Alibaba BOM。两个应用本功能不依赖 `spring-cloud-starter-alibaba-nacos-config`，不写 `spring.config.import`。`local.env.example` 里 `NACOS_CONFIG_ENABLED=false`，`NACOS_SERVER_ADDR=127.0.0.1:8848`。
- Rationale: 规格把「Java 读取配置」放在范围外，并允许关闭配置中心后健康检查仍成功。BOM 先就位，是为了版本只在父 POM 登记一次，本功能并不因此连上 Nacos。
- Alternatives considered: 本功能就引入 starter 并在 local 关闭。多一个启动分支，对空跑没有验收价值。

## Agent 本功能不上报 trace

- Decision: `pyproject.toml` 只放入本功能要启动的锁定依赖：`fastapi` `0.115.6`、`uvicorn` `0.34.0`、`pydantic` `2.10.4`。不加入 `langfuse` 包。进程只监听 `127.0.0.1:8090`。
- Rationale: 规格禁止本功能上报 trace，也禁止对公网或 APP 提供对话。服务端镜像已按客户端 `2.57.0` 的兼容范围选定，客户端留到真正上报的功能再加入。
- Alternatives considered: 先把 `langfuse==2.57.0` 写入依赖但不调用。这是未使用的依赖，不能当作观测已完成。

## 主库迁移

- Decision: Flyway 只加一条迁移：`CREATE EXTENSION IF NOT EXISTS vector`。不建 `docs/data-model.md` 里的业务表。
- Rationale: 规格允许空迁移或只建扩展。扩展迁移能证明 pgvector 可用，又不会提前落业务表。
- Alternatives considered: 完全空迁移。能通过迁移工具，但不能证明向量扩展已安装。

## 健康检查与信封分开

- Decision: Actuator 与 Agent 的 `/health` 使用各自的健康正文。统一信封只用于以后的业务 JSON，本功能用单元测试锁定字段 `code`、`message`、`data`、`traceId`、`ts`，成功时 `code` 为 `0`。不新增业务路由来演示信封。
- Rationale: 规格把健康检查和业务信封分成两条验收。给健康检查套信封会偏离 Actuator 和 Agent 的现有约定。
- Alternatives considered: 增加一个 ping 路由返回信封。规格没有这个路径，多出来的接口会变成未登记的 API。
