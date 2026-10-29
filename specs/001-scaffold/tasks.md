# Tasks: 项目骨架空跑

**Input**: Design documents from `/specs/001-scaffold/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: 计划要求用测试锁住业务信封和 Agent `/health`。这两处先写失败测试再实现。基础设施、Web 空壳和原生工程用 quickstart 手工验收，不另写测试框架。

**Organization**: 按用户故事拆分，每个故事可以单独验收。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件，且不依赖未完成任务）
- **[Story]**: 用户故事标签，仅用户故事阶段使用

## Path Conventions

- 编排：`deploy/`
- Java：`services/java/`
- Agent：`services/agent/`
- Web：`apps/admin-web`、`apps/h5`、`apps/www`、`apps/land`
- 原生：`apps/android`、`apps/ios`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 防止本机环境文件被提交

- [X] T001 在 `.gitignore` 增加 `deploy/env/local.env`，保留 `deploy/env/local.env.example` 可提交

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 所有要读环境变量的故事共用这一份示例。完成前不要写编排和 Java 配置。

- [X] T002 编写 `deploy/env/local.env.example`，只放本机占位值：`PG_HOST`、`PG_PORT`、`PG_DATABASE`、`PG_USERNAME`、`PG_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`、`NACOS_SERVER_ADDR=127.0.0.1:8848`、`NACOS_CONFIG_ENABLED=false`、`LANGFUSE_HOST=http://127.0.0.1:3100`、`NEXTAUTH_URL=http://127.0.0.1:3100`、`NEXTAUTH_SECRET`、`SALT`、`ENCRYPTION_KEY`（64 位十六进制占位）。不得写入生产密钥

**Checkpoint**: 示例文件可被复制为 `deploy/env/local.env`，且该副本被 Git 忽略

---

## Phase 3: User Story 1 - 本地基础设施能起来 (Priority: P1) 🎯 MVP

**Goal**: 一条本地编排拉起商品主库、Redis、Nacos、Langfuse Web 及其自用库，且没有 MySQL

**Independent Test**: 不启动 Java 或 Agent。按 `quickstart.md` 第 1 节启动后，pgvector 扩展存在，Nacos 活性探针为 HTTP 200，Langfuse 根路径为 HTTP 2xx 或 3xx

### Implementation for User Story 1

- [X] T003 [US1] 在 `deploy/docker-compose.local.yml` 写 `postgres`（镜像 `pgvector/pgvector:pg16`）和 `redis`（镜像 `redis:7.4-alpine`），口令来自 `deploy/env/local.env.example` 的变量名
- [X] T004 [US1] 在 `deploy/docker-compose.local.yml` 增加 `nacos`：镜像 `nacos/nacos-server:v2.4.3`，`MODE=standalone`，`NACOS_AUTH_ENABLE=false`，`JVM_XMS=256m`，`JVM_XMX=256m`，发布 `8848` 与 `9848`，不设置 MySQL 数据源
- [X] T005 [US1] 在 `deploy/docker-compose.local.yml` 增加 `langfuse-db`（镜像 `postgres:16-alpine`，独立卷）和 `langfuse-web`（镜像 `langfuse/langfuse:2`，`3100:3000`，`NEXTAUTH_URL=http://127.0.0.1:3100`，库地址指向 `langfuse-db`）

**Checkpoint**: 编排里没有 MySQL。商品主库与 Langfuse 库不是同一个服务

---

## Phase 4: User Story 2 - 两个服务端空壳能编译并报健康 (Priority: P1)

**Goal**: `shop-api-app` 与 `shop-api-admin` 能编译，分别在 8081 与 8082 报健康，并只迁移 pgvector 扩展

**Independent Test**: `mvn -pl shop-api-app,shop-api-admin -am test` 通过。两个健康地址返回 HTTP 200。`shop-api-app` 与 `shop-api-admin` 的 POM 互不依赖。空库启动后只有扩展 `vector`，没有业务表

### Tests for User Story 2

- [X] T006 [US2] 先写失败测试 `services/java/shop-api-common/src/test/java/com/omino/shopai/api/ApiResponseTest.java`，断言成功 JSON 同时含 `code`（数字 0）、`message`、`data`、`traceId`、`ts`

### Implementation for User Story 2

- [X] T007 [US2] 编写 `services/java/pom.xml`：JDK 21、Spring Boot `3.4.11`，`dependencyManagement` 导入 Spring Cloud BOM `2024.0.1` 与 Spring Cloud Alibaba BOM `2023.0.1.2`，模块为 `shop-api-common`、`shop-api-app`、`shop-api-admin`。本功能不声明 Nacos starter
- [X] T008 [US2] 实现 `services/java/shop-api-common/src/main/java/com/omino/shopai/api/ApiResponse.java` 与 `services/java/shop-api-common/pom.xml`，使 T006 通过
- [X] T009 [US2] 添加 `services/java/shop-api-common/src/main/resources/db/migration/V1__enable_pgvector.sql`，内容只有 `CREATE EXTENSION IF NOT EXISTS vector`。放在 common，使两个应用都能看见脚本，又不必互相依赖
- [X] T010 [P] [US2] 创建 `services/java/shop-api-app/`：`pom.xml` 只依赖 `shop-api-common`（含 Web、Actuator、Flyway `11.3.1`、PostgreSQL 驱动 `42.7.5`、Druid `1.2.24`），`AppApiApplication.java`，`application.yml` 与 `application-local.yml`（端口 8081，context-path `/api/app`，`NACOS_CONFIG_ENABLED` 对应关闭，不写 `spring.config.import`）
- [X] T011 [P] [US2] 创建 `services/java/shop-api-admin/`：`pom.xml` 只依赖 `shop-api-common`，不得依赖 `shop-api-app`，`AdminApiApplication.java`，`application.yml` 与 `application-local.yml`（端口 8082，context-path `/api/admin`，同样不连接 Nacos）

**Checkpoint**: 两个进程健康检查不需要登录、联盟密钥或 SSO。Actuator 正文不使用业务信封

---

## Phase 5: User Story 3 - Agent 只在本机报健康 (Priority: P2)

**Goal**: Agent 只监听 `127.0.0.1:8090`，`GET /health` 返回 `{"status":"ok"}`

**Independent Test**: `uv run pytest` 通过，且 `curl http://127.0.0.1:8090/health` 得到上述 JSON。进程不监听 `0.0.0.0`

### Tests for User Story 3

- [X] T012 [US3] 先写失败测试 `services/agent/tests/test_health.py`，请求 `/health` 时期望 HTTP 200 且正文为 `{"status":"ok"}`

### Implementation for User Story 3

- [X] T013 [US3] 编写 `services/agent/pyproject.toml`，锁定 `fastapi` `0.115.6`、`uvicorn` `0.34.0`、`pydantic` `2.10.4`、`pytest` `8.3.4`。不要加入 `langfuse` 包
- [X] T014 [US3] 实现 `services/agent/app/main.py` 的 `GET /health`，使 T012 通过。启动命令绑定 `--host 127.0.0.1`，不提供对话路由

**Checkpoint**: 不启动 Java 也能单独验收 Agent

---

## Phase 6: User Story 4 - 四个 Web 空壳能启动 (Priority: P2)

**Goal**: 四个 Vite 应用能 `npm run dev`，页面为空，没有登录、对话或运营流程

**Independent Test**: 每个目录 `npm ci && npm run dev` 后进程起来。页面上没有登录表单和对话输入

### Implementation for User Story 4

- [X] T015 [P] [US4] 创建 `apps/admin-web/`（`package.json`、`vite.config.ts`、`index.html`、`src/main.ts`、`src/App.vue`）。Vue `3.5.13`、Vite `6.3.5`、TypeScript `5.7.2`、Vue Router `4.5.0`、Pinia `2.3.0`、Element Plus `2.9.1`、axios `1.7.9`。端口 5173。`App.vue` 只有空页面
- [X] T016 [P] [US4] 创建 `apps/h5/`，同样的 Vue 工具链，外加 Tailwind CSS `4.1.0`，不要 Element Plus。端口 5177。`src/App.vue` 只有空页面
- [X] T017 [P] [US4] 创建 `apps/www/`，使用 Vue、Vite、TypeScript、Vue Router，不要 Element Plus，不要 Pinia。端口 5175。`src/App.vue` 只有空页面
- [X] T018 [P] [US4] 创建 `apps/land/`，依赖范围同 `apps/www`。端口 5176。`src/App.vue` 只有空页面，不要邀请业务页

**Checkpoint**: 四个开发服务器可以同时开，端口互不冲突

---

## Phase 7: User Story 5 - 两个原生工程能打开或打出调试包 (Priority: P3)

**Goal**: Android 能打出调试包，iOS 工程能被打开。标识都是 `com.omino.shopai`

**Independent Test**: `./gradlew :app:assembleDebug` 成功。iOS 在 Xcode 16.2 中可打开；没有该环境时检查工程文件与 Bundle ID，并在交付说明里写明未打开

### Implementation for User Story 5

- [X] T019 [P] [US5] 创建 `apps/android/`：Kotlin `2.1.10`、AGP `8.7.3`、compileSdk 35、minSdk 26、Compose BOM `2024.12.01`，`applicationId` 为 `com.omino.shopai`。只保留一个空 Activity，使 `:app:assembleDebug` 通过
- [X] T020 [P] [US5] 创建 `apps/ios/` 的 SwiftUI 工程，Swift `6.0`，最低 iOS 17，Bundle ID `com.omino.shopai`。不要 Flutter、React Native 或 KMP

**Checkpoint**: 原生工程不调用购物或登录接口

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: 把已能执行的命令写进 README，并按 quickstart 做一次总验收

- [X] T021 编写 `README.md`，只复述本功能已提供的命令：复制 `deploy/env/local.env.example`、编排五个服务、两个 Java 进程、Agent、四个 `npm run dev`、Android 调试构建、打开 iOS 工程。不要写登录、对话或联盟步骤
- [X] T022 按 `specs/001-scaffold/quickstart.md` 做一次总验收，确认健康检查、Nacos、Langfuse、编译和空壳都符合 `specs/001-scaffold/contracts/health.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup 无前置
- Foundational 依赖 Setup，并挡住全部用户故事
- 用户故事都依赖 Foundational。US2 的迁移验收需要 US1 的数据库已启动；代码本身不引用 US1
- Polish 依赖要交付的故事都已完成

### User Story Dependencies

- US1 只依赖 Foundational
- US2 只依赖 Foundational；跑通迁移时需要 US1 的 Postgres
- US3、US4、US5 只依赖 Foundational，彼此无关

### Within Each User Story

- US2、US3 的测试先写并确认失败，再写实现
- US2 先有父 POM 和 common，再并行写 app 与 admin
- US1 的三个任务改同一个编排文件，必须按 T003 → T004 → T005

### Parallel Opportunities

- T010 与 T011 可并行
- T015、T016、T017、T018 可并行
- T019 与 T020 可并行
- Foundational 完成后，US3、US4、US5 可与 US1 并行。US2 的代码也可并行，迁移的运行验收放在 US1 之后

---

## Parallel Example: User Story 4

```bash
# 四个空壳目录互不引用，可同时做：
Task: "创建 apps/admin-web/"
Task: "创建 apps/h5/"
Task: "创建 apps/www/"
Task: "创建 apps/land/"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1 与 Phase 2
2. 完成 Phase 3
3. 停下来，只按 quickstart 第 1 节验收基础设施

规格里的三个进程健康检查还要 US2 和 US3。US1 是第一条可单独演示的增量。

### Incremental Delivery

1. US1 证明编排
2. US2 证明两个 Java 进程和信封
3. US3 证明 Agent 只在本机
4. US4 证明四个 Web 空壳
5. US5 证明原生工程
6. Phase 8 把 README 和总验收补齐

### Parallel Team Strategy

Foundational 完成后：一人做 US1，一人做 US2 的编译部分，一人做 US4 或 US5。US2 的数据库迁移放到 US1 的 Postgres 起来之后再跑。

---

## Notes

- 不要创建登录、对话、联盟、SSO、业务表和业务页面
- 不要把 `langfuse` Python 包或 Nacos starter 加进本功能
- 不要复制 mall-guide
- 每个任务做完应能指出对应文件，避免同一文件被并行任务同时改
