# Feature Specification: 项目骨架空跑

**Feature Branch**: `001-scaffold`

**Created**: 2026-10-29

**Status**: Draft

**Input**: User description: "功能：omino-shop-ai 脚手架（001-scaffold）。做：本地编排与环境示例（PostgreSQL 16 + pgvector、Redis 7.4，不要 MySQL）；Java 双产出物空壳与健康检查、统一响应信封骨架、迁移工具能跑空迁移或扩展迁移；Agent 仅本机健康检查；管理后台、消费者 H5、官网、落地页能以开发命令启动；Android 能打出调试包，iOS 工程能打开；包名 com.omino.shopai；目录一次对齐权威目录；README 只复述已有启动命令。不做：登录、对话、联盟、SSO 业务、复制 mall-guide、把全部业务表建完。验收：编排起来后两个服务端与 Agent 的健康检查成功；两个 Java 模块能编译。权威：AGENTS.md §2 §3 §8 §9。"

## Clarifications

### Session 2026-10-29

- Q: 本地的 Nacos 和 Langfuse 怎样才算可访问？ → A: 环境示例里的本机占位值能让容器启动，本机端口返回 HTTP 响应即可。不要求登录，也不要求 Java 或 Agent 连上去。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 本地基础设施能起来 (Priority: P1)

研发在仓库根按文档复制环境示例并启动本地编排后，关系库带向量扩展，缓存可用，配置中心与观测服务可用，且没有第二套关系库。

**Why this priority**: 权威启动命令要同时拉起这些服务。没有它们，后面的配置和观测没有本地落点。

**Independent Test**: 只启动编排，不启动业务进程。关系库接受连接且向量扩展存在，缓存接受连接。Nacos 与 Langfuse Web 在文档给出的本机地址上返回 HTTP 响应。不要求登录，不要求 Java 或 Agent 已连接。编排定义中不出现 MySQL。

**Acceptance Scenarios**:

1. **Given** 已按文档从示例复制本地环境文件，**When** 启动本地编排，**Then** PostgreSQL 16 与 pgvector、Redis 7.4、Nacos、Langfuse Web 及其自用库都处于可用状态。
2. **Given** 已用环境示例中的本机占位值启动编排，**When** 请求文档中的本机地址，**Then** Nacos 在 `127.0.0.1:8848` 返回 HTTP 响应，Langfuse Web 在 `http://127.0.0.1:3100` 返回 HTTP 响应。不要求登录成功。
3. **Given** 编排已启动，**When** 检查编排定义与运行中的服务，**Then** 不存在 MySQL，Nacos 未开启服务发现，Langfuse 的库不是商品主库。

---

### User Story 2 - 两个服务端空壳能编译并报健康 (Priority: P1)

研发启动两个服务端进程后，各自在规定端口和路径前缀上返回健康，且能编译。响应骨架包含统一信封字段。迁移工具至少能执行一次空迁移或仅创建扩展的迁移。

**Why this priority**: 本功能的验收就是这两个健康检查加上编译。没有它们，不能称为骨架完成。

**Independent Test**: 在基础设施已启动的前提下编译两个模块并请求各自健康地址，不需要登录、联盟密钥或 SSO。

**Acceptance Scenarios**:

1. **Given** 基础设施已启动且本地允许不连接配置中心，**When** 启动 APP 进程，**Then** `GET /api/app/actuator/health` 在端口 8181 上成功。
2. **Given** 同上，**When** 启动 Admin 进程，**Then** `GET /api/admin/actuator/health` 在端口 8082 上成功。
3. **Given** 仓库源码，**When** 编译两个 Java 模块，**Then** 编译成功，且两个模块没有互相引用。
4. **Given** 任一进程返回 JSON 业务响应，**When** 查看信封，**Then** 含 `code`、`message`、`data`、`traceId`、`ts`。业务成功时 `code` 为 `0`。
5. **Given** 空库，**When** 进程启动并执行迁移，**Then** 迁移成功，且不创建权威数据模型里的全部业务表。

---

### User Story 3 - Agent 只在本机报健康 (Priority: P2)

研发在本机启动 Agent 后，健康检查成功。进程不向局域网或公网监听。

**Why this priority**: 验收要求第三个健康检查。它不依赖对话或联盟，可以在服务端骨架之后单独证明。

**Independent Test**: 只启动 Agent，请求本机健康地址得到成功，并确认监听地址是本机回环。

**Acceptance Scenarios**:

1. **Given** 按锁定版本安装依赖，**When** 在 8190 端口启动 Agent，**Then** `GET http://127.0.0.1:8190/health` 成功。
2. **Given** Agent 已启动，**When** 查看监听地址，**Then** 只绑定本机，不绑定所有网卡。

---

### User Story 4 - 四个 Web 空壳能启动 (Priority: P2)

研发能分别启动管理后台、消费者 H5、官网、落地页的开发服务器。页面可以是空的。

**Why this priority**: 验收要求各端空壳能启动。它们不依赖登录和业务接口。

**Independent Test**: 在每个应用目录执行文档中的开发命令，进程起来且没有业务页面。

**Acceptance Scenarios**:

1. **Given** 依赖已安装，**When** 分别执行四个应用的开发命令，**Then** 管理后台、消费者 H5、官网、落地页各自启动。
2. **Given** 四个空壳已启动，**When** 打开页面，**Then** 不出现登录、对话、联盟或 SSO 业务流程。

---

### User Story 5 - 两个原生工程能打开或打出调试包 (Priority: P3)

研发能让 Android 打出调试包，并能用指定工具打开 iOS 工程。两端包标识都是 `com.omino.shopai`。

**Why this priority**: 原生工程只证明目录和工程文件有效，不证明购物流程。可以晚于服务端健康检查。

**Independent Test**: 执行 Android 调试构建；在 macOS 上打开 iOS 工程。不安装电商应用，不登录。

**Acceptance Scenarios**:

1. **Given** Android 工程，**When** 执行调试构建，**Then** 构建成功，应用标识为 `com.omino.shopai`。
2. **Given** iOS 工程与文档要求的 Xcode 版本，**When** 打开工程，**Then** 工程可被打开，Bundle ID 为 `com.omino.shopai`。

---

### Edge Cases

- 未从示例复制环境文件就启动：启动失败并指出缺少配置。不得静默连上另一套库，不得把密钥写进仓库。
- 健康检查在没有联盟密钥、没有登录、没有 SSO 时仍须成功。
- 两个 Java 进程不得监听同一端口。APP 前缀只出现在 APP 进程，Admin 前缀只出现在 Admin 进程。
- Agent 绑定到非本机地址时，本功能视为未完成。
- 编排或依赖中出现 MySQL，视为未完成。
- Nacos 开启服务发现，或把密钥明文放进 Nacos，视为未完成。
- Langfuse 的库被当成商品主库，或把生产密钥写入 Git，视为未完成。环境示例中的本机占位值不算生产密钥。
- Nacos 或 Langfuse 的端口没有 HTTP 响应，视为未完成。要求登录后才算通过，也视为超出本功能。
- README 若写出本功能没有的登录、对话或联盟步骤，视为未完成。
- 目录若另起包名，或把业务类提前放进 Controller，视为未完成。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 本地编排 MUST 启动 `postgres`、`redis`、`nacos`、`langfuse-web`、`langfuse-db`。PostgreSQL MUST 为 16 并带 pgvector。Redis MUST 为 7.4。Nacos MUST 使用镜像 `nacos/nacos-server:v2.4.3`，只做配置中心，MUST NOT 做服务发现。Langfuse Web MUST 在 `http://127.0.0.1:3100` 返回 HTTP 响应，其数据库只服务 Langfuse。MUST NOT 提供 MySQL。环境示例放在 `deploy/env/local.env.example`，并给出 `NACOS_SERVER_ADDR` 与 `LANGFUSE_HOST` 的上述本地值。示例 MAY 包含仅用于本机启动的占位值。占位值不是生产密钥。真实密钥不得写入 Git，也不得明文写入 Nacos。验收只要求上述两个地址返回 HTTP 响应，不要求登录，不要求 Java 或 Agent 连接它们。
- **FR-002**: Java 父工程 MUST 使用权威文档锁定的 JDK 21、Maven 与 Spring Boot 版本，并拆成 `shop-api-common`、`shop-api-app`、`shop-api-admin`。APP 与 Admin MUST NOT 互相 import。
- **FR-003**: APP 进程 MUST 使用上下文路径 `/api/app`、端口 8181。Admin 进程 MUST 使用 `/api/admin`、端口 8082。二者健康检查路径分别为 `GET /api/app/actuator/health` 与 `GET /api/admin/actuator/health`。
- **FR-004**: 业务 JSON 响应 MUST 使用信封字段 `code`、`message`、`data`、`traceId`、`ts`。本功能只要求骨架存在，不要求实现登录、对话或联盟的业务错误码。
- **FR-005**: 迁移工具 MUST 能在空库上成功执行。迁移内容只允许为空，或只创建运行所需扩展。MUST NOT 建齐权威数据模型中的全部业务表。
- **FR-006**: Agent MUST 使用权威文档锁定的 Python 与 FastAPI 版本，只监听 `127.0.0.1:8190`，并提供 `GET /health`。MUST NOT 对公网或对 APP 提供对话接口。
- **FR-007**: `apps/admin-web`、`apps/h5`、`apps/www`、`apps/land` MUST 能用各自的开发命令启动。依赖版本 MUST 使用权威文档已锁定的版本。空壳 MUST NOT 包含登录页、对话页或运营业务页。
- **FR-008**: Android 工程 MUST 能打出调试包。iOS 工程 MUST 能被打开。两端标识 MUST 为 `com.omino.shopai`。MUST NOT 使用 Flutter、React Native 或 Kotlin Multiplatform。
- **FR-009**: 本功能新增的目录与入口 MUST 与权威文档的目录结构一致，不得另起包名。尚未属于本功能的业务类可以不创建。
- **FR-010**: README MUST 复述权威启动命令中的本地编排，包含 `postgres`、`redis`、`nacos`、`langfuse-web`、`langfuse-db`。MUST NOT 把登录、对话或联盟写成当前可执行步骤。
- **FR-011**: 本功能 MUST NOT 实现登录、对话、联盟转链、管理后台 SSO 会话，MUST NOT 自建管理员密码表，MUST NOT 复制 mall-guide 的源码、Mapper、表名或包名。

### Out of Scope

- 手机号登录、JWT 业务、对话 run、SSE、联盟适配器、订单同步、积分与钱包。
- 管理后台 SSO 与任何管理员密码表。
- Vue / H5 / iOS / Android 的业务页面。
- Agent 的意图图、工具调用，以及向 Langfuse 上报 trace。观测服务本身必须已启动。
- 向 Nacos 写入运营热配置，以及 Java 在本功能中读取这些配置。本地健康检查仍允许关闭配置中心连接。
- 权威数据模型中的全部业务表。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 按文档用本机占位值启动本地编排后，关系库与缓存可用，配置中心和观测 Web 在文档地址上返回 HTTP 响应；两个服务端健康检查与 Agent 健康检查共 3 次全部成功。不要求登录 Nacos 或 Langfuse，也不要求 Java 或 Agent 连接它们。
- **SC-002**: 两个 Java 模块在一次构建中编译通过。
- **SC-003**: 四个 Web 空壳各自能用文档中的开发命令启动，启动后不出现登录或对话流程。
- **SC-004**: Android 调试构建成功；在文档要求的桌面环境下 iOS 工程能被打开。两端标识都是 `com.omino.shopai`。
- **SC-005**: 只阅读 README 与权威启动章节的人，能在不查阅其它功能文档的情况下完成上述检查。

## Assumptions

- 本功能的使用者是仓库研发，不是购物用户或运营。
- 审阅确认 Nacos 与 Langfuse 纳入本功能，与权威启动命令一致。可访问指：环境示例中的本机占位值能让容器启动，且 Nacos 在 `127.0.0.1:8848`、Langfuse Web 在 `http://127.0.0.1:3100` 返回 HTTP 响应。不要求登录，不要求 Java 或 Agent 连接。运营配置内容和 Agent 实际上报不在本功能。
- 权威文档锁定了 Nacos 服务端镜像和 Langfuse 的 Python 客户端 `2.57.0`，没有锁定 Langfuse 服务端镜像。计划里写明所选服务端镜像，不得改动已锁定的客户端版本。
- 健康检查不依赖配置中心里已有业务配置，也不依赖 SSO、短信、支付或联盟密钥。本地允许关闭配置中心连接后仍能通过健康检查。
- iOS 打开工程只在 macOS 与文档锁定的 Xcode 版本上验收。没有该环境时，以工程文件存在且 Bundle ID 正确为当前可验证项，并在交付说明里写明未在本机打开。
- 版本号、端口、路径和目录以 `AGENTS.md` §2、§3、§8、§9 为准。本规格不另锁一套版本。
