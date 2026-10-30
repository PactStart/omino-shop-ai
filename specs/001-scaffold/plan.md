# Implementation Plan: 项目骨架空跑

**Branch**: `001-scaffold` | **Date**: 2026-10-29 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-scaffold/spec.md`

## Summary

搭一套能在本机空跑的骨架：编排拉起商品主库、Redis、Nacos 和 Langfuse；两个 Java 进程分别在 8181 与 8082 报健康并完成只含 pgvector 扩展的迁移；Agent 只在本机 8190 报健康；四个 Web 空壳和两个原生工程能启动或打开。不实现登录、对话、联盟、SSO，Java 不读 Nacos，Agent 不上报 trace。

技术选择见 [research.md](research.md)。Langfuse 服务端用官方主版本镜像 `langfuse/langfuse:2`，以匹配已锁定的 Python 客户端 `2.57.0` 和权威启动命令里的两个服务名。

## Technical Context

**Language/Version**: Java 21（Temurin `21.0.8`）与 Maven `3.9.9`；Python `3.12.8` 与 uv `0.5.18`；Vue `3.5.13` / Vite `6.3.5` / TypeScript `5.7.2`；Kotlin `2.1.10`；Swift `6.0`

**Primary Dependencies**: Spring Boot `3.4.11`、Flyway `11.3.1`、PostgreSQL 驱动 `42.7.5`、Druid `1.2.24`；FastAPI `0.115.6`、Uvicorn `0.34.0`、Pydantic `2.10.4`；管理端 Element Plus `2.9.1`；H5 Tailwind CSS `4.1.0`。版本全部来自 `AGENTS.md` §2，本计划不另锁。

**Storage**: 商品主库 `pgvector/pgvector:pg16`；缓存 `redis:7.4-alpine`；Nacos `nacos/nacos-server:v2.4.3` 单机嵌入式存储；Langfuse 自用库 `postgres:16-alpine`。不用 MySQL。

**Testing**: Java 用 JUnit 5（Boot starter test）锁信封字段，并检查两个应用模块互不依赖。Agent 用 pytest `8.3.4` 锁 `/health`。基础设施与端口用 [quickstart.md](quickstart.md) 的 curl 验收。不引入未登记的 Testcontainers。

**Target Platform**: 本地 Docker Compose v2；Java 进程；Agent 只绑定回环；四个 Vite 开发服务器；Android 调试包；iOS 工程可被 Xcode 16.2 打开。

**Project Type**: 多端空壳加两个 HTTP 进程。业务页面和业务 API 不在本功能。

**Performance Goals**: 无吞吐指标。本地编排起来后，健康检查与 Nacos、Langfuse 的 HTTP 探针能在一次手工验收里完成。

**Constraints**: 端口与前缀按规格固定。Agent 不得绑定 `0.0.0.0`。真实密钥不进 Git；环境示例只放本机占位值。`deploy/env/local.env` 加入忽略列表。不复制 mall-guide。不建管理员密码表。

**Scale/Scope**: 一个本地编排文件、三个 Java 模块、一个 Agent 健康进程、四个 Web 空壳、两个原生工程。不创建 `AGENTS.md` §3 里尚未属于本功能的业务类，也不写 test/prod 编排和 nginx 站点。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 计划前 | 设计后 |
| --- | --- | --- |
| I. 一套 Java，两个产出物 | 通过。只建 common / app / admin，端口分开 | 通过。契约里两个健康地址端口不同，模块互不依赖 |
| II. mall-guide 只读 | 通过。不读其源码来填实现 | 通过。数据模型只有本仓库的扩展和基础设施 |
| III. 管理后台只走 omino-sso | 通过。本功能不做登录，不建密码表 | 通过。Web 空壳没有登录页 |
| IV. 密钥与转链只在 Java，对话链路固定 | 通过。本功能不做转链和对话。Agent 只监听本机 | 通过。占位值只在环境示例；健康响应不含密钥 |
| V. 主库唯一，先改文档再实现 | 通过。主库是 PostgreSQL + pgvector，无行为变更要改权威文档 | 通过。迁移只有 `vector` 扩展 |
| VI. 先检索再实现 | 通过。Langfuse 镜像按官方兼容矩阵选定 | 通过。见 research.md，未改已锁定客户端版本 |

无未解决的 NEEDS CLARIFICATION。无需要豁免的宪章例外。

## Project Structure

### Documentation (this feature)

```text
specs/001-scaffold/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── health.md
│   └── envelope.md
└── tasks.md              # 由 /speckit-tasks 生成，本命令不创建
```

### Source Code (repository root)

```text
deploy/
├── docker-compose.local.yml
└── env/local.env.example

services/java/
├── pom.xml
├── shop-api-common/
├── shop-api-app/
└── shop-api-admin/

services/agent/
├── pyproject.toml
└── app/main.py

apps/admin-web/
apps/h5/
apps/www/
apps/land/
apps/android/
apps/ios/

README.md
```

**Structure Decision**: 目录名和包名按 `AGENTS.md` §3，只创建本功能要启动的入口。APP 依赖 common，Admin 依赖 common，二者互不依赖。`/internal/v1` 本功能不挂路由。业务 Controller、Agent 图和运营页面留到后续功能。

## Complexity Tracking

本功能无需要豁免的宪章例外。

## Phase 0

研究结论在 [research.md](research.md)。没有留下待澄清项。

## Phase 1

- 数据：见 [data-model.md](data-model.md)。没有业务实体。
- 契约：见 [contracts/health.md](contracts/health.md) 与 [contracts/envelope.md](contracts/envelope.md)。
- 验收步骤：见 [quickstart.md](quickstart.md)。

设计完成后已按上表重查宪章，六条均为通过。
