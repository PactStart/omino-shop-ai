# omino-shop-ai 项目规范

> 本文件是**索引 + 技术栈 + 目录 + 约束 + 信封 + 环境变量**的权威正文。
> 数据模型、HTTP、对话契约已拆到 `docs/`（见 §4 表）；**改哪类规则就改哪份文档**，再改代码。同类字段不得在两份里各写一套。
> 与权威文档冲突的实现一律视为缺陷。Cursor 会读取本文件；细粒度约定放 `.cursor/rules/*.mdc`，不得与本文件及拆出文档冲突。
>
> 参考仓库（**只读，禁止复制代码、禁止粘贴其包名/类名/表结构**）：
>
> - `mall-guide`：弄懂「搜索 → 详情缓存 → 转链 → 按平台游标增量同步订单」的流程后，在本仓库**重写**适配层。
> - `omino-sso`：仅管理后台走 OIDC Authorization Code + PKCE（S256）。APP 端身份独立，不走 SSO。

---

## 1. 项目概述


| 项     | 内容                                                                         |
| ----- | -------------------------------------------------------------------------- |
| 项目名   | `omino-shop-ai`（包名/Bundle `com.omino.shopai`；**展示名待定**，候选见 `docs/app-ui.md`） |
| 一句话描述 | 面向消费者的 AI 购物助手：用文字或语音说出需求，由 Agent 识别意图并跨电商平台推荐商品，支持转链跳转第三方 APP 下单，并回看订单。   |
| 目标用户  | ① 终端用户（对话选品、看详情、跳转购买、查订单、邀请）② 运营/管理员（配置平台、海报模板、观测对话与同步任务）③ 本仓库研发（按本规范实现各端） |


**核心功能**

1. **对话选品**：文字 / 语音 / 图片 / 第三方商品链接 → 意图与槽位（含追问）→ 记忆三分法消解指代 → Tool 调 Java 搜品 → SSE 推文本、商品卡或**合集卡**（条数过多）。可回看浏览/收藏。
2. **详情与购买**：点卡片进详情（Java）；详情页「去购买」校验联盟授权、本机是否安装对应 APP，再转链 deeplink。
3. **账号、订单与收益**：微信 / 手机号 / Apple / Google 登录；每人一个不可变邀请码；对话内可查自身信息、电商订单、返佣、邀请；联盟授权/取消授权走 HITL 卡片；邀请海报由 **APP / H5 端**把落地 URL 编成二维码贴进模板（多模板左右滑切换），再分享微信 / QQ 或保存相册。
4. **对话积分**：记录每次 LLM 的 token 明细；按比例扣积分；余额不足则中途终止并下发购积分卡；套餐支付走 `credit_order`，渠道 **微信 / 支付宝 / 返佣钱包余额**（只允许钱包→积分，禁止积分提现或转回钱包）。
5. **支持**：规则类走知识库检索（后期可换 MCP）；无法回答或用户反馈则建工单。
6. **管理后台**：运营、工单、同步状态、知识库、积分套餐、邀请海报、版本/渠道包、公告、站内信、运行时配置；登录必须对接 `omino-sso`。
7. **官网与落地页**：品牌官网；邀请落地 H5（链接含邀请码，引导下载或拉起 APP）。
8. **运营投放**：版本更新（含 Android 渠道包）、按渠道/端/版本的特殊行为、公告、点对点站内信。

**端组成**（禁止用 Flutter / RN / KMP / 一套 Dart 出双端）


| 端            | 职责                                  | 实现                       |
| ------------ | ----------------------------------- | ------------------------ |
| APP（Android） | 对话、商品卡、详情、deeplink、登录、订单、邀请海报分享     | Kotlin + Jetpack Compose |
| APP（iOS）     | 同上，独立工程                             | Swift + SwiftUI          |
| APP（H5）      | 与原生同一套页面与 `/api/app`；**Demo 第一周主验收端**；上帝视角看指定账号 | Vue 3 + Tailwind（`apps/h5`） |
| 官网           | 品牌、功能介绍、商店下载                        | Vue 3（`apps/www`）        |
| 落地页 H5       | 邀请链 `/i/{inviteCode}`、唤起 APP / 引导下载 | Vue 3（`apps/land`）       |
| 管理后台         | 运营与观测                               | Vue 3（`apps/admin-web`）  |
| APP 接口       | C 端 HTTP API                        | Java Spring Boot 产出物 1   |
| 管理后台接口       | 管理 HTTP API                         | 同一套 Java 代码，产出物 2        |
| Agent        | AI 对话、意图、记忆、编排搜品                    | Python LangGraph         |


APP 页面层级见 [`docs/app-ui.md`](docs/app-ui.md)（视觉与信息架构；Android / iOS / H5 共用同一套页面，不覆盖本文件的字段 / SSE / 接口）。

---

## 2. 技术栈表（锁定版本号）

版本一经锁定不得随意升级；升级须先改本表并说明回归范围。首次脚手架以本表写入 lockfile。

### 2.1 Java 双产出物（一套代码）


| 组件                       | 坐标 / 名称                                                       | 锁定版本            | 用途                                                                                 |
| ------------------------ | ------------------------------------------------------------- | --------------- | ---------------------------------------------------------------------------------- |
| JDK                      | Eclipse Temurin                                               | `21.0.8`        | 语言与运行时                                                                             |
| Maven                    | Apache Maven                                                  | `3.9.9`         | 构建                                                                                 |
| 框架                       | `org.springframework.boot:spring-boot-starter-parent`         | `3.4.11`        | Web / 校验 / Actuator                                                                |
| ORM                      | `com.baomidou:mybatis-plus-spring-boot3-starter`              | `3.5.9`         | PostgreSQL 访问                                                                      |
| 连接池                      | `com.alibaba:druid-spring-boot-3-starter`                     | `1.2.24`        |                                                                                    |
| PG 驱动                    | `org.postgresql:postgresql`                                   | `42.7.5`        |                                                                                    |
| Redis                    | `org.springframework.boot:spring-boot-starter-data-redis`     | 随 Boot `3.4.11` | 商品短缓存、限流、SSE 连接锁                                                                   |
| JSON                     | `com.fasterxml.jackson.core:jackson-databind`                 | 随 Boot          | 禁止再引入 Fastjson                                                                     |
| 校验                       | `spring-boot-starter-validation`                              | 随 Boot          |                                                                                    |
| OpenAPI                  | `org.springdoc:springdoc-openapi-starter-webmvc-ui`           | `2.8.5`         | 两个产出物都要能打开文档：`/api/app/v3/api-docs`、`/api/app/swagger-ui.html` 与 Admin 对应路径。Controller 的每个映射方法必须有 `@Operation`，类上有 `@Tag`。文档路径不要求登录 |
| HTML 清洗 | `org.jsoup:jsoup` | `1.21.1` | 只清洗公告、站内信、版本说明的有限 HTML。不用它抓网页。MIT。放 `shop-api-common` |
| 安全（APP）                  | `spring-boot-starter-security` + Nimbus JWT                   | 随 Boot / `9.47` | APP JWT                                                                            |
| 安全（Admin）                | Spring Security OAuth2 Client                                 | 随 Boot          | 对接 omino-sso                                                                       |
| 迁移                       | `org.flywaydb:flyway-core` + `flyway-database-postgresql`     | `11.3.1`        | 唯一 schema 变更方式                                                                     |
| 定时                       | `spring-boot-starter-quartz`                                  | 随 Boot          | 订单增量同步（本阶段不用 XXL-JOB）                                                              |
| 测试                       | `spring-boot-starter-test`                                    | 随 Boot          | JUnit 5                                                                            |
| Lombok                   | `org.projectlombok:lombok`                                    | `1.18.48`       | 实体必须用 `@Getter` `@Setter`，禁止手写 getter/setter，禁止 `@Data`。对外 DTO 若使用 Lombok，Jackson 必须能通过访问器读到字段。由 `1.18.36` 升到 `1.18.48`：前者的注解处理器在 JDK 25 上初始化失败（`TypeTag :: UNKNOWN`），而本机没有 JDK 21。语言级别仍是 21。只用 Getter/Setter，不用 `@Data`、`@Builder`、`@SneakyThrows` |
| Spring Cloud BOM         | `org.springframework.cloud:spring-cloud-dependencies`         | `2024.0.1`      | 与 Boot `3.4.11` 对齐；**仅**给 Nacos Config 用                                           |
| Spring Cloud Alibaba BOM | `com.alibaba.cloud:spring-cloud-alibaba-dependencies`         | `2023.0.1.2`    | 官方 2025.x 绑 Boot 3.5，不用。本坐标在 Boot 3.4 + Cloud 2024.0 上可用（只开 Config）                |
| Nacos Config             | `com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-config` | 随 SCA BOM       | 运营热配置；**禁止**引入 Discovery / Sentinel / Seata / RocketMQ；**禁止**因本依赖把 Fastjson 写进业务代码 |
| DAO XML 生成器              | `com.baomidou:mybatis-plus-generator`                         | `3.5.9`         | 只生成 Mapper 接口与 XML。不生成 Entity、Service、Controller。只放 test 范围，不进两个可运行 jar。不用它的 Service 模板。Apache-2.0 |
| 微信支付                     | `com.github.wechatpay-apiv3:wechatpay-java`                   | `0.2.17`        | 只用 APIv3 下单与回调验签。不用于向用户打款，不带入示例商户站点。Apache-2.0。只进 `shop-api-app` |
| 支付宝                      | `com.alipay.sdk:alipay-sdk-java`                              | `4.40.1044.ALL` | 回调验签（APP）与单笔转账（Admin）。不引第二个支付宝库。Apache-2.0。若依赖树抬高已锁定版本，改用仍为 Apache-2.0 且不抬版本的最新 `4.40.x` |
| 淘宝客 SDK                  | `com.taobao:taobao-sdk-java`                                  | `20250925`      | 只用物料搜索、商品详情、万能转链、订单明细，以及授权换票和渠道备案。另装同版本 `sources`。不用推荐、退款、处罚、活动、口令。厂商许可，不是 MIT / Apache-2.0 / BSD，因官方出站必须用它，记为例外。只进 `shop-api-app`。传递进来的 Fastjson 不得在业务代码里调用 |
| 京东联盟 SDK                 | `com.jd:open-api-sdk`                                         | `20250925`      | 只用商品查询、子联盟转链、订单行查询。不用京粉、大字段、物料、榜单、活动。许可例外同上。只进 `shop-api-app` |
| 拼多多 SDK                  | `com.pdd:pop-sdk`                                             | `1.10.16`       | 只用商品搜索、详情、推广链接、增量订单，以及备案查询和授权页。不用推荐、PID 生成、招商。许可例外同上。只进 `shop-api-app` |
| 唯品会 SDK                  | `com.vip:vop-sdk`                                             | `20250214094310` | 只用商品查询、按商品号详情、按商品号转链、订单列表，以及授权检查、备案链接和解绑。商品转链不用渠道链接。不用相似推荐、猜你喜欢、退款、PID 生成。许可例外同上。只进 `shop-api-app` |
| 唯品会传输                   | `com.vip:osp-sdk`                                             | `20250214094310` | 只作为 `vop-sdk` 的调用运行时，不单独发起业务接口。许可例外同上。只进 `shop-api-app` |


Maven 模块：`shop-api`（parent）→ `shop-api-common`、`shop-api-app`、`shop-api-admin`。两个 Spring Boot 主类打两个 fat jar。parent 的 `dependencyManagement` 导入上述两个 BOM。

路由前缀（硬约束）：


| 产出物              | `server.servlet.context-path` 与网关前缀 | 业务 API             |
| ---------------- | ----------------------------------- | ------------------ |
| `shop-api-app`   | `/api/app`                          | `/api/app/v1/**`   |
| `shop-api-admin` | `/api/admin`                        | `/api/admin/v1/**` |


禁止两个产出物监听同一端口。本地默认 APP `8181`、Admin `8082`。Agent `8190`，管理后台 `5183`，Redis 宿主机 `6381`。这些值避开同机的 omino-sso：IdP `8080`、管理 API `8081`、门户 `5173`、后台 `5174`、一体化后台容器 `8090`、Redis 宿主机 `6380`。

### 2.2 Agent（Python）


| 组件      | 包名                              | 锁定版本                        | 用途                                          |
| ------- | ------------------------------- | --------------------------- | ------------------------------------------- |
| 语言      | CPython                         | `3.12.8`                    |                                             |
| 包管理     | `uv`                            | `0.5.18`                    | 禁止裸 `pip install` 进运行环境                     |
| API     | `fastapi`                       | `0.115.6`                   | 仅被 Java 内网调用                                |
| 服务      | `uvicorn`                       | `0.34.0`                    |                                             |
| Agent 图 | `langgraph`                     | `0.2.70`                    | 意图 → 槽位 → 工具 → 回复 的唯一编排                     |
| LLM 抽象  | `langchain-core`                | `0.3.29`                    | 消息 / 工具协议                                   |
| 厂商适配    | `langchain-openai`              | `0.3.3`                     | **DeepSeek** OpenAI 兼容协议（`OPENAI_API_BASE`=`https://api.deepseek.com`）；不另引 DeepSeek SDK |
| 观测      | `langfuse`                      | `2.57.0`                    | Trace / Prompt / 评分；**默认**。LangSmith 本阶段不引入 |
| 校验      | `pydantic`                      | `2.10.4`                    | 工具入参/出参                                     |
| HTTP    | `httpx`                         | `0.28.1`                    | 调 Java `/internal/v1/**` 工具                 |
| 图状态     | `langgraph-checkpoint-postgres` | 与 `langgraph` `0.2.70` 配套锁定 | 工作记忆 Checkpointer                           |
| PG      | `psycopg[binary]`               | `3.2.4`                     | Checkpointer + 语义表                          |
| 测试      | `pytest`                        | `8.3.4`                     |                                             |


Agent 对外只提供内网接口（[`docs/api-internal.md`](docs/api-internal.md)），**不对公网、不对 APP 直连**。对话入口永远是 Android / iOS / H5 → `shop-api-app`（SSE）→ Agent。意图/SSE 以 [`docs/conversation.md`](docs/conversation.md) 为准；节点说明见 [`docs/agent.md`](docs/agent.md)。

### 2.3 APP（Android / iOS 原生）

**禁止** Flutter、React Native、Kotlin Multiplatform、一套 UI 代码出双端。产品与接口契约相同，工程分开。微信登录 / 分享、QQ 分享的 **官方 SDK 精确版本** 按 §7.1.6 检索后写入本表再引入（须 Android / iOS 各自一行）。

#### Android（`apps/android`）


| 组件                     | 锁定版本                                                   | 用途                               |
| ---------------------- | ------------------------------------------------------ | -------------------------------- |
| 语言                     | Kotlin `2.1.10`                                        |                                  |
| JDK（编译）                | `17`（AGP 要求；运行时仍是本机）                                   | 与服务端 JDK 21 无关                   |
| Android Gradle Plugin  | `8.7.3`                                                |                                  |
| compileSdk / targetSdk | `35`                                                   |                                  |
| minSdk                 | `26`                                                   |                                  |
| UI                     | Jetpack Compose BOM `2024.12.01`                       | Material 3；页面对齐 `docs/app-ui.md` |
| Activity               | `androidx.activity:activity-compose` `1.9.3`           | 空壳把 Compose 挂到 Activity。只用 `setContent` |
| 网络                     | OkHttp `4.12.0` + Retrofit `2.11.0`                    | SSE 用 OkHttp 事件源；禁止再引一套 HTTP 栈   |
| JSON                   | kotlinx-serialization-json `1.7.3`                     |                                  |
| 安全存储                   | AndroidX Security Crypto `1.1.0-alpha06`（引入前若升稳定版须改本表） | refresh token                    |
| 协程                     | kotlinx-coroutines-android `1.9.0`                     |                                  |


语音转写、已装电商 APP 检测、微信（**开放平台移动应用**，禁止用小程序当主登录）、QQ/Google 登录与分享：官方 SDK，**引入前改本表锁定坐标+版本**。仅上报白名单包名，禁止上传完整应用列表。邀请海报二维码在 **客户端** 生成并贴进模板矩形。**友盟**（统计/渠道）、**极光**（推送）：引入前按 §7.1.6 锁定 Android/iOS 精确坐标；开关仍走 `app_user.push_enabled`。

#### iOS（`apps/ios`）


| 组件    | 锁定版本             | 用途                                      |
| ----- | ---------------- | --------------------------------------- |
| 语言    | Swift `6.0`      |                                         |
| Xcode | `16.2`           |                                         |
| 最低系统  | iOS `17.0`       |                                         |
| UI    | SwiftUI（随 Xcode） | 页面对齐 `docs/app-ui.md`                   |
| 网络    | `URLSession`     | SSE 用原生 streaming；禁止再引 Alamofire，除非先改本表 |
| 安全存储  | Keychain         | refresh token                           |


Sign in with Apple 用 AuthenticationServices。微信为开放平台 **iOS 应用**。友盟 + 极光：引入前锁定版本。Universal Links 打开落地页同源路径 `/i/{inviteCode}`。邀请海报二维码在端上生成。

### 2.4 管理后台（Vue）

**UI 建议（已锁定）**：`Vue 3 + Vite + TypeScript + Pinia + Vue Router + Element Plus`。

理由：与 `omino-sso` 管理端同族，表格/表单/权限菜单成本最低；本项目后台是运营后台不是营销站，不选 Ant Design Vue / Naive UI，避免两套后台心智。


| 组件  | 包名                        | 锁定版本     | 用途                                                                          |
| --- | ------------------------- | -------- | --------------------------------------------------------------------------- |
| 框架  | `vue`                     | `3.5.13` |                                                                             |
| 构建  | `vite`                    | `6.3.5`  |                                                                             |
| Vue 插件 | `@vitejs/plugin-vue`      | `5.2.4`  | 编译 `.vue`。peer 含 Vite `^6`。不用它的 SSR                              |
| 语言  | `typescript`              | `5.7.2`  | `strict: true`                                                              |
| 路由  | `vue-router`              | `4.5.0`  |                                                                             |
| 状态  | `pinia`                   | `2.3.0`  |                                                                             |
| 请求  | `axios`                   | `1.7.9`  | `withCredentials: true`（Admin Cookie 会话）                                    |
| UI  | `element-plus`            | `2.9.1`  | 全局注册允许（独立 admin 应用）                                                         |
| 图标  | `@element-plus/icons-vue` | `2.3.1`  |                                                                             |
| SSO | `oidc-client-ts`          | `3.1.0`  | 仅当走纯 SPA 时；**推荐 BFF**：浏览器只打 Admin API，由 Java 完成 code 交换（与 omino-sso 接入指南一致）。本阶段不引入 |
| 二维码 | `qrcode`                  | `1.5.4`  | 只用 Canvas 把占位邀请地址画进海报矩形。不向服务端要合成图。MIT。不引入第二个二维码库 |


Admin 登录：**MUST** 走 omino-sso OIDC Authorization Code + PKCE S256。禁止自建管理员账号密码表。

### 2.4.1 官网与落地页（Vue，非 Admin）

与 Admin **同族版本**（vue / vite / typescript / vue-router），**禁止** Element Plus（营销站不是表格后台）。不要 React。


| 应用  | 目录          | 端口（local） | 路由                                                 |
| --- | ----------- | --------- | -------------------------------------------------- |
| 官网  | `apps/www`  | `5175`    | `/` 品牌与下载（App Store / 应用商店 / 二维码）                  |
| 落地页 | `apps/land` | `5176`    | `/i/:inviteCode` 展示邀请码、打开 APP、商店兜底；`/download` 纯下载 |


`axios` `1.7.9` 仅落地页可选调用公开预览接口；官网可纯静态。部署：`nginx:1.27-alpine` 托管 `dist`，见 `deploy/nginx/www.conf`、`land.conf`。

### 2.4.2 消费者 H5（`apps/h5`，与原生 APP 同契约）

与 Admin **同族** vue / vite / typescript / vue-router / pinia / axios 锁定版本。**禁止** Element Plus。样式跟 [`docs/app-ui.md`](docs/app-ui.md)：Tailwind CSS `4.1.0`（MIT）与 Vite 插件 `@tailwindcss/vite` `4.1.0`（只把 Tailwind 接进 Vite）+ shadcn/ui 的 Token/组件套路（按需拷贝 Vue 实现，不引入第二套组件库）。页面、路由 ID、SSE 事件与原生一致。

| 项 | 规定 |
| --- | --- |
| local 端口 | `5177` |
| 请求 | 同 APP：`Authorization: Bearer` + 客户端头（[`api-app.md`](docs/api-app.md)） |
| 海报 | 浏览器 Canvas 贴二维码；多模板左右滑 |
| 渠道 | 固定 `web` |
| `clientType` | 固定 `h5` |
| 上帝视角 | 仅 Admin 签发短时 JWT（`gv=1`）；H5 顶栏常驻「上帝视角」条。`prod` 默认关闭 |
| Demo | **第一周主验收端**；登录仅手机号验证码 |

部署：`deploy/nginx/h5.conf`。H5 **不是**落地页 `apps/land`。

### 2.5 基础设施


| 组件         | 锁定版本                                       | 用途                                                                                                  |
| ---------- | ------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| PostgreSQL | `16` + 扩展 `pgvector`                       | **唯一主库**（交易表 + 工单/钱包 + Agent Checkpointer + 语义向量）。镜像用 `pgvector/pgvector:pg16`。Java **不再使用 MySQL**。 |
| Redis      | `7.4`                                      | 商品详情/搜索短缓存、限流、SSE 锁；**不是**主库                                                                        |
| Docker     | `27.x`                                     | 镜像构建                                                                                                |
| Compose    | `v2`                                       | `local` / `test` / `prod` 分文件                                                                       |
| 反向代理       | `nginx:1.27-alpine`                        | 管理后台 + 官网 + 落地页 + 消费者 H5 静态资源 + API 反代                                                              |
| 对象存储       | 阿里云 OSS SDK（引入前登记精确版本）                     | 本阶段可空配置                                                                                             |
| Nacos      | `2.4.3`（服务端镜像 `nacos/nacos-server:v2.4.3`） | Java 配置中心；Compose local 单机。**不做**服务发现。密钥仍走环境变量，禁止明文进 Nacos                                          |


### 2.6 Java 配置分层（yml vs Nacos）

两个产出物各自：

- `application.yml`：公共结构（`spring.config.import` 指向 Nacos、共享默认）。
- `application-{local,test,prod}.yml`：**随发布、改完要发版/重启** 的项。
- Nacos：`refresh=true`，**改完立即对运行中实例生效** 的运营项。


| 放哪                      | 内容                                                                                                                                                                                                                                               |
| ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `application-{env}.yml` | 端口、`context-path`、数据源/Redis **地址形态**、Flyway、线程池、OpenAPI 开关、日志级别默认、JWT TTL、Quartz 是否启用。profile 名与 `APP_ENV` 一致：`local` / `test` / `prod`                                                                                                          |
| Nacos Config            | 须热更新的运营项：`credit.tokens-per-credit`、`credit.enforce`、`commission.user-share-rate`（用户分成比例，默认 `0.80`）、限流阈值、对话 run 超时、订单同步间隔、各联盟适配器 enable、购积分卡文案开关、邀请 lookup 限流、`app.channels` 渠道白名单等。DataId：`shop-api-app-{env}.yaml` / `shop-api-admin-{env}.yaml`；Group：`OMINO_SHOP_AI`。`INVITE_LAND_BASE_URL`、协议 URL 随环境发版，放 yml 不放 Nacos。提示词与模型分流在库表，不放 Nacos |
| 环境变量（§9）                | 密钥与连接口令：`PG_PASSWORD`、JWT、联盟、支付、SSO、`NACOS_SERVER_ADDR`。**禁止**写入 Git 中的 yml，**禁止** Nacos 明文存密钥                                                                                                                                                   |


`test`/`prod` 启动必须连上 Nacos，连不上则失败退出。`local` 允许 `NACOS_CONFIG_ENABLED=false`，热配置回落到 yml 默认值（Demo）。业务代码读配置经 `@ConfigurationProperties` + `@RefreshScope`（仅 Nacos 项），禁止散落 `System.getenv` 解析运营数字。

APP 与 Admin 对每次 HTTP 请求打一行访问日志：方法、路径、`traceId`、`ts`、HTTP 状态、信封 `code` 与 `message`、耗时。`traceId` 与信封相同。日志里的 `ts` 是上海时间，格式 `yyyyMMdd HH:mm:ss`；有信封时把信封时间换算成这个格式，没有信封时用打日志这一刻。HTTP 状态 ≥ 400，或信封 `code` 非 0 时，同一行再带查询字符串和请求体。不记录请求头。口令、令牌、验证码字段打成 `***`。`/actuator/` 不打。

---

## 3. 目录结构（精确到核心文件）

```
omino-shop-ai/
├── AGENTS.md
├── README.md
├── Makefile                         # 本地启动入口。app/admin/agent 先读 local.infra.env，再读自己的 env
├── .cursor/rules/project.mdc          # alwaysApply，指向本文件
├── deploy/
│   ├── docker-compose.local.yml
│   ├── docker-compose.test.yml
│   ├── docker-compose.prod.yml
│   ├── env/
│   │   ├── local.infra.env.example    # Compose，以及 app/admin/agent 先加载的共享项
│   │   ├── local.app.env.example
│   │   ├── local.admin.env.example
│   │   ├── local.agent.env.example
│   │   ├── test.env.example
│   │   └── prod.env.example
│   └── nginx/
│       ├── admin.conf
│       ├── www.conf
│       ├── land.conf
│       └── h5.conf
├── apps/
│   ├── www/                           # 官网。Vite 读本目录 .env，不读 deploy/env
│   │   ├── .env.example
│   │   ├── package.json
│   │   ├── vite.config.ts
│   │   ├── index.html
│   │   └── src/
│   ├── h5/                            # 消费者 H5 APP（同 app-ui）。Vite 读本目录 .env
│   │   ├── .env.example
│   │   ├── package.json
│   │   ├── vite.config.ts
│   │   ├── index.html
│   │   └── src/
│   ├── land/                          # 邀请落地 H5。Vite 读本目录 .env
│   │   ├── .env.example
│   │   ├── package.json
│   │   ├── vite.config.ts
│   │   ├── index.html
│   │   └── src/
│   │       └── views/InviteLand.vue   # /i/:inviteCode
│   ├── android/                       # Kotlin + Compose，包名 com.omino.shopai
│   │   ├── .env.example               # API_BASE。当前 Gradle 不读取
│   │   ├── settings.gradle.kts
│   │   └── app/src/main/java/com/omino/shopai/
│   ├── ios/                           # SwiftUI，Bundle ID com.omino.shopai
│   │   ├── .env.example               # API_BASE。当前 Xcode 工程不读取
│   │   └── App/
│   ├── admin-web/                     # Vite 读本目录 .env
│   │   ├── .env.example
│   │   ├── package.json
│   │   ├── vite.config.ts
│   │   ├── index.html
│   │   └── src/
│   │       ├── main.ts
│   │       ├── App.vue
│   │       ├── router/index.ts
│   │       ├── stores/session.ts
│   │       ├── api/http.ts
│   │       └── views/
│   │           ├── login-callback.vue
│   │           ├── dashboard.vue
│   │           ├── orders.vue
│   │           ├── conversations.vue
│   │           ├── releases.vue
│   │           ├── runtime-configs.vue
│   │           ├── announcements.vue
│   │           ├── inbox.vue
│   │           ├── users.vue
│   │           ├── user-detail.vue
│   │           └── god-view.vue
├── services/
│   ├── java/
│   │   ├── pom.xml                    # parent，packaging=pom
│   │   ├── shop-api-common/           # 两产出物共享：信封、DTO、实体、Mapper（DAO）
│   │   │   ├── pom.xml
│   │   │   └── src/main/
│   │   │       ├── java/com/omino/shopai/
│   │   │       │   ├── api/ApiResponse.java
│   │   │       │   ├── api/HttpAccessLogFilter.java
│   │   │       │   ├── api/ErrorCodes.java
│   │   │       │   ├── enums/Platform.java
│   │   │       │   ├── enums/Intent.java
│   │   │       │   ├── dto/goods/GoodsCard.java
│   │   │       │   ├── dto/chat/ChatTurnRequest.java
│   │   │       │   ├── entity/AppUser.java
│   │   │       │   ├── entity/AppOrder.java
│   │   │       │   ├── entity/Ticket.java
│   │   │       │   ├── entity/TicketLog.java
│   │   │       │   ├── entity/Wallet.java
│   │   │       │   ├── entity/Withdrawal.java
│   │   │       │   ├── entity/CreditAccount.java
│   │   │       │   ├── entity/CreditLedger.java
│   │   │       │   ├── entity/CreditPackage.java
│   │   │       │   ├── entity/CreditOrder.java
│   │   │       │   ├── entity/LlmUsage.java
│   │   │       │   ├── entity/InvitePosterTemplate.java
│   │   │       │   ├── entity/AppRelease.java
│   │   │       │   ├── entity/AppRuntimeConfig.java
│   │   │       │   ├── entity/Announcement.java
│   │   │       │   ├── entity/InboxMessage.java
│   │   │       │   ├── entity/GoodsCollection.java
│   │   │       │   ├── entity/GoodsCollectionItem.java
│   │   │       │   ├── entity/GoodsFavorite.java
│   │   │       │   ├── entity/ChatSuggestion.java
│   │   │       │   ├── entity/AgentPrompt.java
│   │   │       │   ├── entity/LlmModel.java
│   │   │       │   ├── mapper/AppUserMapper.java      # DAO（MyBatis-Plus）
│   │   │       │   ├── mapper/AppOrderMapper.java
│   │   │       │   ├── mapper/TicketMapper.java
│   │   │       │   ├── mapper/TicketLogMapper.java
│   │   │       │   ├── mapper/WalletMapper.java
│   │   │       │   ├── mapper/WithdrawalMapper.java
│   │   │       │   ├── mapper/CreditAccountMapper.java
│   │   │       │   ├── mapper/CreditLedgerMapper.java
│   │   │       │   ├── mapper/CreditPackageMapper.java
│   │   │       │   ├── mapper/CreditOrderMapper.java
│   │   │       │   ├── mapper/LlmUsageMapper.java
│   │   │       │   ├── mapper/InvitePosterTemplateMapper.java
│   │   │       │   ├── mapper/AppReleaseMapper.java
│   │   │       │   ├── mapper/AppRuntimeConfigMapper.java
│   │   │       │   ├── mapper/AnnouncementMapper.java
│   │   │       │   ├── mapper/InboxMessageMapper.java
│   │   │       │   ├── mapper/GoodsCollectionMapper.java
│   │   │       │   ├── mapper/GoodsCollectionItemMapper.java
│   │   │       │   ├── mapper/GoodsFavoriteMapper.java
│   │   │       │   ├── mapper/ChatSuggestionMapper.java
│   │   │       │   ├── mapper/AgentPromptMapper.java
│   │   │       │   └── mapper/LlmModelMapper.java
│   │   │       └── resources/mapper/                  # 复杂 SQL 的 XML，禁止业务写在 XML 里拼联盟逻辑
│   │   ├── shop-api-app/
│   │   │   ├── pom.xml
│   │   │   └── src/main/
│   │   │       ├── java/com/omino/shopai/app/
│   │   │       │   ├── AppApiApplication.java
│   │   │       │   ├── config/                        # Security、WebClient、Flyway 以外的 Bean
│   │   │       │   ├── security/                      # JWT、internal token 过滤器
│   │   │       │   ├── web/app/                       # 对 Android/iOS：/api/app/v1
│   │   │       │   │   ├── ChatController.java            # POST turns + GET runs/{id}/events
│   │   │       │   │   ├── GoodsController.java
│   │   │       │   │   ├── AuthController.java
│   │   │       │   │   ├── OrderController.java
│   │   │       │   │   ├── WalletController.java
│   │   │       │   │   ├── CreditController.java
│   │   │       │   │   ├── InviteController.java      # 邀请码、生效中的海报模板列表
│   │   │       │   │   ├── AppMetaController.java     # 检查更新、运行时配置、公告、协议 URL
│   │   │       │   │   ├── InboxController.java
│   │   │       │   │   ├── MeController.java          # 资料、推送开关、注销
│   │   │       │   │   ├── AuthorizationController.java # 授权状态、取消授权、淘宝回调
│   │   │       │   │   └── FileController.java
│   │   │       │   ├── web/internal/                  # 对 Agent：/internal/v1，禁止再调 Agent
│   │   │       │   │   ├── InternalGoodsController.java
│   │   │       │   │   ├── InternalMeController.java
│   │   │       │   │   ├── InternalOrderController.java
│   │   │       │   │   ├── InternalWalletController.java
│   │   │       │   │   ├── InternalTicketController.java
│   │   │       │   │   └── InternalKbController.java
│   │   │       │   ├── service/                       # 用例编排，无 HTTP 类型
│   │   │       │   │   ├── chat/ChatTurnService.java      # POST 开 run
│   │   │       │   ├── chat/ChatWorkerImpl.java        # 调 Agent，XADD Redis
│   │   │       │   ├── chat/ChatEventPump.java        # ≤100ms 读 Redis → SSE
│   │   │       │   ├── chat/ChatCancelService.java    # SET cancel 键；不假设 Worker 在本机
│   │   │       │   │   ├── goods/GoodsQueryService.java
│   │   │       │   │   ├── goods/GoodsConvertLinkService.java
│   │   │       │   │   ├── goods/GoodsCollectionService.java
│   │   │       │   │   ├── goods/GoodsFavoriteService.java
│   │   │       │   │   ├── auth/UnionAuthService.java
│   │   │       │   │   ├── order/OrderQueryService.java
│   │   │       │   │   ├── wallet/WalletService.java
│   │   │       │   │   ├── ticket/TicketService.java
│   │   │       │   │   ├── invite/AffiliateService.java
│   │   │       │   │   ├── invite/InvitePosterService.java  # 只下发模板图与二维码矩形；禁止服务端合成成品
│   │   │       │   │   ├── kb/KbSearchService.java
│   │   │       │   │   ├── credit/CreditBillingImpl.java
│   │   │       │   │   ├── app/AppReleaseService.java
│   │   │       │   │   ├── app/AppRuntimeConfigService.java
│   │   │       │   │   ├── app/AnnouncementService.java
│   │   │       │   │   └── inbox/InboxService.java
│   │   │       │   ├── adapter/
│   │   │       │   │   ├── agent/IAgentGateway.java    # WebClient SSE 调 Python
│   │   │       │   │   ├── ocr/ImageParseClient.java
│   │   │       │   │   ├── pay/PayClient.java         # 积分套餐支付；SDK 按 §7.1.6 引入前先改 §2
│   │   │       │   │   └── platform/                  # 联盟官方 SDK 封装，禁止进 service 直接 new SDK
│   │   │       │   │       ├── IPlatformGoods.java
│   │   │       │   │       ├── TaobaoGoodsImpl.java
│   │   │       │   │       └── JdGoodsImpl.java
│   │   │       │   ├── job/OrderSyncJob.java
│   │   │       │   └── support/sse/SseEmitterSupport.java
│   │   │       └── resources/
│   │   │           ├── application.yml
│   │   │           ├── application-local.yml
│   │   │           ├── application-test.yml
│   │   │           ├── application-prod.yml
│   │   │           └── db/migration/            # Flyway，PG
│   │   └── shop-api-admin/
│   │       ├── pom.xml
│   │       └── src/main/
│   │           ├── resources/application.yml
│   │           ├── resources/application-local.yml
│   │           ├── resources/application-test.yml
│   │           ├── resources/application-prod.yml
│   │           └── java/com/omino/shopai/admin/
│   │               ├── AdminApiApplication.java
│   │               ├── config/
│   │               ├── security/
│   │               ├── sso/SsoClientConfig.java
│   │               ├── web/
│   │               │   ├── AdminOrderController.java
│   │               │   ├── AdminTicketController.java
│   │               │   ├── AdminWalletController.java
│   │               │   ├── AdminKbController.java
│   │               │   ├── AdminCreditController.java
│   │               │   ├── AdminInvitePosterController.java
│   │               │   ├── AdminReleaseController.java
│   │               │   ├── AdminRuntimeConfigController.java
│   │               │   ├── AdminAnnouncementController.java
│   │               │   ├── AdminInboxController.java
│   │               │   ├── AdminUserController.java
│   │               │   ├── AdminGodViewController.java
│   │               │   ├── AdminChatSuggestionController.java
│   │               │   ├── AdminAgentPromptController.java
│   │               │   └── AdminLlmModelController.java
│   │               └── service/
│   │                   ├── order/AdminOrderService.java
│   │                   ├── ticket/AdminTicketService.java
│   │                   ├── wallet/AdminWithdrawalService.java
│   │                   ├── credit/AdminCreditService.java
│   │                   ├── release/AdminReleaseService.java
│   │                   ├── config/AdminRuntimeConfigService.java
│   │                   ├── announcement/AdminAnnouncementService.java
│   │                   ├── inbox/AdminInboxService.java
│   │                   ├── user/AdminUserService.java
│   │                   ├── godview/AdminGodViewService.java
│   │                   ├── chat/AdminChatSuggestionService.java
│   │                   ├── agent/AdminAgentPromptService.java
│   │                   └── llm/AdminLlmModelService.java
│   └── agent/
│       ├── pyproject.toml
│       ├── app/
│       │   ├── main.py
│       │   ├── api/invoke.py                 # 内网 SSE
│       │   ├── graph/state.py                # AgentState
│       │   ├── graph/builder.py              # 节点+边装配
│       │   ├── graph/nodes/ingest.py
│       │   ├── graph/nodes/intent.py
│       │   ├── graph/nodes/slot_fill.py
│       │   ├── graph/nodes/coref.py
│       │   ├── graph/nodes/memory_load.py
│       │   ├── graph/nodes/memory_write.py
│       │   ├── graph/nodes/clarify.py
│       │   ├── graph/subgraphs/shop.py
│       │   ├── graph/subgraphs/account.py
│       │   ├── graph/subgraphs/order.py
│       │   ├── graph/subgraphs/earn.py
│       │   ├── graph/subgraphs/support.py
│       │   ├── graph/nodes/hitl.py
│       │   ├── graph/nodes/reply_sse.py
│       │   ├── tools/java_client.py          # 全部 Tool 的 httpx 封装
│       │   └── observability/langfuse_setup.py
│       └── tests/
│           └── test_intent.py
└── docs/
    ├── sdd.md
    ├── product-input.md
    └── agent.md                           # Agent 详解；与本文件冲突以本文件为准
```

尚未生成的源码目录以上面为目标结构，脚手架必须一次对齐，禁止另起包名。

**Java 分层（强制，与 MyBatis-Plus 对齐）**


| 层           | 包                                              | 允许                               | 禁止                                                               |
| ----------- | ---------------------------------------------- | -------------------------------- | ---------------------------------------------------------------- |
| web         | `..web.app` / `..web.internal` / admin `..web` | 参数校验、鉴权上下文、调 service、SSE/HTTP 映射 | 写 SQL、调联盟 SDK、new Mapper                                         |
| service     | `..service.<领域>`                               | 事务、用例、编排 adapter + mapper        | 依赖 Servlet/SseEmitter 类型（SSE 细节放 `support`）；跨模块 import admin↔app |
| mapper（DAO） | `com.omino.shopai.mapper`（在 **common**）        | MyBatis-Plus `BaseMapper` 与自定义查询 | 业务 if/else、调 HTTP、当 Repository 再套一层空转发                           |
| entity      | `com.omino.shopai.entity`（在 **common**）        | 与 [`data-model.md`](docs/data-model.md) 表一一对应 | 当对外 API 用（对外用 `dto`）                                             |
| dto         | `com.omino.shopai.dto`（在 **common**）           | 请求/响应/内部 Tool 入出参                | 混入 persistence 注解当实体                                             |
| adapter     | `..adapter.platform` / `agent` / `ocr`         | 外部系统；入出参为 dto                    | 被 web 直接调用（必须经 service）                                          |


不引入 Spring Data JPA。口语里的 repository = 本项目的 `mapper`。Admin 与 App **共用** common 的 entity/mapper，各自只写自己的 web + service。公共查询可抽 `common` 里的 `support` 工具类，但 **带事务的用例仍放各产出物的 service**，避免 common 依赖 Spring Web。

命名与摆放：

- 业务接口以 `I` 开头，其实现类以 `Impl` 结尾。`mapper` 是 DAO，保持 `XxxMapper`，不加 `I`。
- 业务类禁止以 `Mybatis` 开头，禁止以 `Store` 结尾。
- 异常放 `com.omino.shopai.exception`，类名以 `Exception` 结尾。
- 对外的请求体、响应体放 `dto`（common 的 `com.omino.shopai.dto`，或仅 app 使用的 `com.omino.shopai.app.dto`）。类名以 `Dto` 结尾。只被一个 Controller 使用的请求记录放在该 Controller 所在目录的 `dto` 包，不写在 Controller 类里面。分页只有 `PageDto<T>`，字段仍是 `list`、`page`、`size`、`total`，不要再按资源各写一个分页记录。
- 无状态工具放 `support` 或 `app.util`，不和用例、联盟适配器放在同一个包。
- 同一件事不要拆成只被一处调用的一串小类型。
- 信封的 `traceId` 和 `ts` 不由 Controller 填写。访问过滤器在进入 Controller 之前生成这一次请求的追踪号，放在请求属性上，访问日志用同一个号。Controller 返回 `ApiResponse.ok(data)`。`ResponseBodyAdvice` 在写出 JSON 之前把追踪号和上海时间写进信封；异常信封走同一条，成功和失败是同一个号。微信/支付宝回调原文、登录重定向和 SSE 不是信封，Advice 不改它们。`@Operation.summary` 写这个接口做什么，不写路径。

---

## 4. 契约文档索引

JSON、`Content-Type: application/json`、UTF-8。时间 `ISO-8601`（带 `Z` 或时区偏移）。金额与积分为字符串、单位元、两位小数。分页 `page` 从 `1` 起。信封与错误码见本文 §5。

鉴权：

- APP / H5：`Authorization: Bearer <access_token>`（登录除外）；业务请求另带客户端头（见 [`api-app.md`](docs/api-app.md) §4.8）。
- Admin：浏览器 Cookie（HttpOnly）；Admin API 作 RP 对接 omino-sso。
- Agent：仅内网；`X-Internal-Token`。Java `/internal/v1/**` 另需 `X-User-Id`（须为当前对话用户）。**禁止** internal 再调 Agent。

| 文档 | 权威范围 |
| --- | --- |
| [`docs/conversation.md`](docs/conversation.md) | 意图枚举、一张图、记忆三分法、SSE 事件、开 run / 取消 / 积分扣费 |
| [`docs/api-app.md`](docs/api-app.md) | 消费者 HTTP：短信登录、商品、转链、浏览/收藏/合集、积分支付、邀请、版本头、站内信、订单 |
| [`docs/api-admin.md`](docs/api-admin.md) | 运营 HTTP：用户档案、套餐、海报模板、版本、配置、公告、私信、用户视角 |
| [`docs/api-internal.md`](docs/api-internal.md) | `POST /internal/v1/graph/invoke` 与 Tool 白名单 |
| [`docs/data-model.md`](docs/data-model.md) | PostgreSQL 表 |
| [`docs/app-ui.md`](docs/app-ui.md) | 消费者页面 |
| [`docs/admin-ui.md`](docs/admin-ui.md) | 管理后台页面 |
| [`docs/ops-metrics.md`](docs/ops-metrics.md) | 运营指标口径、汇总表、只读接口与后台页面。指标表与字段只写在这一份 |
| [`docs/admin-access.md`](docs/admin-access.md) | 管理后台角色、权限点、菜单与操作的可见范围。不在本仓库建权限表 |

交叉字段只在一处写死，其它文档链接过去。改行为先改对应文档，再改代码。

## 5. 统一响应格式

所有 HTTP JSON API（APP / Admin / Agent）使用同一信封。业务成功时 HTTP 状态为 `200` 且 `code` 为 `0`。业务失败时 HTTP 仍可为 `200`（APP 友好），`code` 为非 0；传输层错误（网关、未登录）可用 `401/403/429`。`traceId` 一次 HTTP 请求一个：进入 Controller 前生成，写出响应时写入信封，访问日志用同一个号。`ts` 是写出时的上海时间。

### 5.1 成功模板

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "goodsId": "g_jd_100012345678"
  },
  "traceId": "01J8TRACEEXAMPLE0000000000",
  "ts": "2026-10-27T10:15:30+08:00"
}
```

列表分页时 `data` 固定为：

```json
{
  "list": [],
  "page": 1,
  "size": 20,
  "total": 0
}
```

### 5.2 错误模板

```json
{
  "code": 40001,
  "message": "text 不能为空",
  "data": null,
  "traceId": "01J8TRACEEXAMPLE0000000000",
  "ts": "2026-10-27T10:15:30+08:00",
  "error": {
    "field": "text",
    "reason": "NOT_BLANK"
  }
}
```


| code    | 含义                                                  |
| ------- | --------------------------------------------------- |
| `0`     | 成功                                                  |
| `40001` | 参数校验失败                                              |
| `40100` | 未登录或 access_token 失效                                |
| `40300` | 已登录但无权                                              |
| `40301` | 转链时平台未联盟授权（`NEED_AUTH`）                             |
| `40302` | 积分不足（`NEED_CREDIT`）；对话终态以 SSE `credit_exhausted` 为准 |
| `40303` | 上帝视角未开启（`GOD_VIEW_DISABLED`）                         |
| `40400` | 资源不存在                                               |
| `40900` | 幂等冲突（`clientMsgId` 已成功受理）                           |
| `42900` | 限流                                                  |
| `50000` | 未分类内部错误                                             |
| `50010` | 上游电商平台失败                                            |
| `50020` | Agent 失败                                            |


`error` 可空。禁止把堆栈、SQL、联盟密钥写入 `message`。

---

## 7. 架构约束

### 7.1 必须遵守（11）

1. **一套 Java，两个产出物**：公共逻辑只放 `shop-api-common`；APP 与 Admin **不得**互相 import 对方模块。对外前缀只有 `/api/app` 与 `/api/admin`；`/internal/v1` 仅挂在 app 进程，**不是**第三产出物。
2. **mall-guide 只读参考**：允许阅读搜品/详情/转链/订单游标；**禁止**复制源码、Mapper、表名、包名。
3. **管理后台身份 = omino-sso**：Authorization Code + PKCE S256。APP 登录与 SSO **隔离**。
4. **密钥与转链只在 Java**：联盟密钥、PID、relationId 只在 Java 环境变量。对话 **POST 开 run → Worker 调 Agent → 事件入 Redis → GET SSE 泵出**；Tool 只调 `/internal/v1`。点击后再转链。断开 SSE 不得打断生成。邀请海报由 **客户端** 贴码，Java 只存模板与矩形。
5. **主库唯一 PostgreSQL+pgvector**；商品不落全量库。意图用 [`conversation.md`](docs/conversation.md) 点分枚举；导购与客服同图分流。Langfuse `traceId` 贯穿。新行为先改对应权威文档再测试再实现。
6. **先检索再实现，禁止盲目造轮子**：遇到不熟悉的能力（SSE、OIDC、向量检索、OSS 直传、验证码、联盟 SDK 封装等），**不要**立刻从零手写协议/编解码/会话机。先在 GitHub 检索维护活跃（近 12 个月有提交或 issue 响应）、许可证明确（MIT / Apache-2.0 / BSD；**拒绝 GPL / AGPL**）、star 与生态匹配的同类项目，优先引入或对照其用法适配本仓库。引入依赖须先改本文件 §2 锁定**精确版本**并说明用哪部分、不用哪部分；不得与已锁版本冲突。本条**不**授权复制 `mall-guide` 源码。
7. **实体用 Lombok**：`entity` 上的持久化类必须用 Lombok `@Getter` 与 `@Setter`，需要无参构造时加 `@NoArgsConstructor`。禁止手写 getter/setter，禁止 `@Data`。对外 DTO 若使用 Lombok，Jackson 必须能通过访问器读到字段。
8. **不建数据库外键**：表与表之间不建数据库外键。关联列只保存对方主键，由应用在事务里校验存在性与一致性。允许 `CHECK`、唯一约束和索引。
9. **枚举字段用枚举类型**：实体上的枚举字段必须定义 Java 枚举，业务代码使用枚举常量，禁止用裸数字或散落字符串表示同一取值。平台、订单状态、结算状态、授权状态的判断用枚举，不要再维护一份同含义的字符串集合。库存仍是约定的字符串或 `0`/`1`，用 MyBatis-Plus `@EnumValue` 映射，不把枚举名写入数据库。数量、排序、版本号、token 数、像素和超时不是枚举。
10. **Controller 必须有 OpenAPI**：每个映射方法必须有 `@Operation`，类上有 `@Tag`。`summary` 写这个接口做什么，不写 URL。APP 与 Admin 都提供可访问的 `/v3/api-docs` 与 Swagger UI。文档路径不要求登录，也不被客户端头拦截。
11. **注释**：除 mapper（DAO 接口与 XML）外，每个函数正上方都要有 Javadoc，写明每个参数和返回值。注解写在这段注释和函数之间。多步事务、状态机、验签和分支在关键步骤写行注释，说明这一步在保证什么。注释写意图，不重复方法名。

### 7.2 非目标 / 本阶段不做（7）

1. **不做**自营电商购物车、商品支付、第三方售后；商品下单仍在电商 APP。钱包与提现只处理 **本平台返佣**。**例外**：对话积分套餐 `credit_order`；允许用返佣 `wallet.available` **购买积分**（`channel=wallet`）。**禁止**积分提现、禁止积分转回钱包。
2. **不做**把 mall-guide 当依赖 jar 或 HTTP 网关封装上线。
3. **不做** LangSmith、多 Agent 进程、开放插件市场、对话直连 MCP（知识库 MCP 只替换 `search_kb`）。
4. **不做** APP 接 omino-sso，也不做管理后台微信/Apple 登录。
5. **不做**人工实时坐席、客服编造物流/政策；工单 P0 只建单，运营在 Admin 处理。Mem0/Letta 不作为主记忆库。
6. **不做** Flutter / React Native / KMP；消费者端只有 Android、iOS 原生与 **H5**（`apps/h5`）。上帝视角默认不上生产。
7. **不做美团**：无 `meituan` 平台码、无美团联盟密钥、无适配器/授权/订单同步。平台枚举只有 `taobao` `jd` `pdd` `vip`。

---

## 8. 启动命令

工作目录均为仓库根。首次执行 `make env`：四份 `deploy/env/local.*.env` 缺哪份就从对应 example 复制，已有则不覆盖。

本地日常用根目录 `Makefile`。`make up` 只读 `local.infra.env`。`make app`、`make admin`、`make agent` 先加载 `local.infra.env`，再加载自己那份。Java 的 yml 默认仍是 `PG_PORT=5432`、`REDIS_PORT=6381`；本机端口被占用时只改 `local.infra.env`，不要改 yml 默认值，也不要在 app/admin/agent 三份里再写一遍库口令。未导入环境变量就直接跑 `mvn`，会连到默认端口上的别的数据库。

必须相同、只写在 `local.infra.env` 的项：`PG_PASSWORD`、`LLM_CONFIG_KEY`、`JWT_ACCESS_SECRET`、`AGENT_INTERNAL_TOKEN`。前端不读 `deploy/env`。Vite 读各应用目录的 `.env`（没有时 `make admin-web` / `h5` / `www` / `land` 从 `.env.example` 复制）。`API_BASE` 放在 `apps/android/.env` 与 `apps/ios/.env`。

```bash
make help
make up          # PostgreSQL+pgvector / Redis / Nacos / Langfuse
make app         # APP API，默认 :8181
make admin       # Admin API，默认 :8082
make agent       # Agent，默认 :8190，仅监听本机
make test
make admin-web   # :5183
make h5          # :5177
make www         # :5175
make land        # :5176
```

Makefile 实际执行的命令：

```bash
docker compose -f deploy/docker-compose.local.yml --env-file deploy/env/local.infra.env up -d postgres redis nacos langfuse-web langfuse-db

# 先 set -a && . deploy/env/local.infra.env && . deploy/env/local.app.env && set +a
cd services/java && mvn -pl shop-api-app -am spring-boot:run -Dspring-boot.run.profiles=local
# Admin 把第二份换成 local.admin.env
cd services/java && mvn -pl shop-api-admin -am spring-boot:run -Dspring-boot.run.profiles=local

# 先 set -a && . deploy/env/local.infra.env && . deploy/env/local.agent.env && set +a
cd services/agent && uv sync && uv run uvicorn app.main:app --reload --port 8190 --host 127.0.0.1

# 各应用目录没有 .env 时，从该目录的 .env.example 复制后再 npm run dev
cd apps/admin-web && npm ci && npm run dev
cd apps/www && npm ci && npm run dev
cd apps/land && npm ci && npm run dev
cd apps/h5 && npm ci && npm run dev

cd apps/android && ./gradlew :app:assembleDebug
# API_BASE 在 apps/android/.env。模拟器示例 http://10.0.2.2:8181/api/app。当前 Gradle 不读取

open apps/ios/App.xcodeproj
# API_BASE 在 apps/ios/.env。示例 http://127.0.0.1:8181/api/app。当前 Xcode 工程不读取
```

分环境部署：

```bash
docker compose -f deploy/docker-compose.test.yml --env-file deploy/env/test.env up -d --build
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/env/prod.env up -d --build
```

健康检查：`GET /api/app/actuator/health`、`GET /api/admin/actuator/health`、`GET http://127.0.0.1:8190/health`。

---

## 9. 环境变量清单

未标默认值的均为必填（对应环境）。密钥类禁止写入 Git。

本地文件（example 可提交，复制出的 `local.*.env` 与各应用 `.env` 不入库）：

| 文件 | 谁读 | 放什么 |
| --- | --- | --- |
| `deploy/env/local.infra.env` | Compose；`make app` / `admin` / `agent` 先加载 | `PG_*`、`REDIS_*`、`NACOS_*`、`APP_ENV`、`TZ`、Langfuse 容器变量，以及必须相同的 `LLM_CONFIG_KEY`、`JWT_ACCESS_SECRET`、`AGENT_INTERNAL_TOKEN` |
| `deploy/env/local.app.env` | `make app` 在 infra 之后加载 | 联盟、短信、支付回调验签、邀请与协议 URL、JWT 有效期、`AGENT_BASE_URL` |
| `deploy/env/local.admin.env` | `make admin` 在 infra 之后加载 | SSO、会话密钥、上帝视角、支付宝打款 |
| `deploy/env/local.agent.env` | `make agent` 在 infra 之后加载 | `OPENAI_*`、`JAVA_APP_INTERNAL_URL`。Langfuse 与内网令牌用 infra 里的值 |
| `apps/<应用>/.env` | 该 Vite / 原生工程 | §9.5。不进 `deploy/env` |

不要在四份里设置 `SERVER_PORT`。APP 默认 `8181`，Admin 默认 `8082`。

### 9.1 公共 / 数据


| 变量                     | 端            | 说明                                  |
| ---------------------- | ------------ | ----------------------------------- |
| `APP_ENV`              | 全部           | `local`                             |
| `PG_HOST`              | Java / Agent |                                     |
| `PG_PORT`              | Java / Agent | 默认 `5432`                           |
| `PG_DATABASE`          | Java / Agent | 默认 `omino_shop_ai`                  |
| `PG_USERNAME`          | Java / Agent |                                     |
| `PG_PASSWORD`          | Java / Agent | 密钥                                  |
| `REDIS_HOST`           | Java / Agent |                                     |
| `REDIS_PORT`           | Java / Agent | 默认 `6381`。避开 omino-sso 宿主机 `6380` |
| `REDIS_PASSWORD`       | Java / Agent | 可空                                  |
| `TZ`                   | 全部           | 默认 `Asia/Shanghai`                  |
| `NACOS_SERVER_ADDR`    | Java         | 如 `127.0.0.1:8848`；`test`/`prod` 必填 |
| `NACOS_NAMESPACE`      | Java         | 可空；分环境隔离                            |
| `NACOS_CONFIG_ENABLED` | Java         | 默认 `true`；仅 `local` 可 `false`       |
| `LLM_CONFIG_KEY`       | Java APP / Admin | AES-GCM 密钥，≥32 字节。加密 `llm_model.api_key_enc`。两产出物必须相同，本地只写在 `local.infra.env`。禁止进 Git 与 Nacos |


### 9.2 APP API（`shop-api-app`）


| 变量                                                                      | 说明                                                      |
| ----------------------------------------------------------------------- | ------------------------------------------------------- |
| `SERVER_PORT`                                                           | 默认 `8181`                                               |
| `JWT_ACCESS_SECRET`                                                     | HS256 密钥，≥32 字节。APP 签发，Admin 上帝视角校验。本地只写在 `local.infra.env` |
| `JWT_ACCESS_TTL_SECONDS`                                                | 默认 `1800`                                               |
| `JWT_REFRESH_TTL_SECONDS`                                               | 默认 `1209600`                                            |
| `AGENT_BASE_URL`                                                        | 如 `http://127.0.0.1:8190`                               |
| `AGENT_INTERNAL_TOKEN`                                                  | 与 Agent 共享。本地只写在 `local.infra.env`                     |
| `WECHAT_APP_ID` / `WECHAT_APP_SECRET`                                   | 微信**开放平台移动应用**（不是小程序）；登录与分享                             |
| `APPLE_CLIENT_ID` / `APPLE_TEAM_ID` / `APPLE_KEY_ID` / `APPLE_P8`       | Sign in with Apple                                      |
| `GOOGLE_CLIENT_ID`                                                      | Google 登录                                               |
| `SMS_PROVIDER`                                                          | `mock` / `chuanglan`；`prod` 禁止 mock。创蓝对齐 omino-sso：`SMS_CHUANGLAN_URL` / `ACCOUNT` / `PASSWORD` / `TEMPLATE_ID`（模板变量 key 硬编码 `param1`） |
| `UNION_TAOBAO_APP_KEY` / `UNION_TAOBAO_APP_SECRET`                       | 淘宝客。不读取推广位 PID                                           |
| `UNION_TAOBAO_INVITE_CODE`                                              | 淘宝渠道备案邀请码。空则授权回调无法写入 relation_id。不下发客户端 |
| `UNION_TAOBAO_OAUTH_REDIRECT`                                           | 淘宝授权回调的完整 HTTPS 地址，须在开放平台登记。如 `https://api.example.com/api/app/v1/me/authorizations/taobao/callback`。空则不签发授权链接 |
| `UNION_JD_APP_KEY` / `UNION_JD_APP_SECRET`                              | 京东联盟。不读取站点号                                             |
| `UNION_PDD_CLIENT_ID` / `UNION_PDD_CLIENT_SECRET`                       | 拼多多                                                     |
| `UNION_VIP_APP_KEY` / `UNION_VIP_APP_SECRET`                            | 唯品会                                                     |
| `PAY_WECHAT_PUBLIC_KEY` / `PAY_WECHAT_PUBLIC_KEY_ID` / `PAY_WECHAT_API_V3_KEY` | 微信支付回调验签。任一为空则拒绝入账，不向微信发探测请求。SDK 坐标锁定前见 §2 |
| `PAY_ALIPAY_PUBLIC_KEY` | 支付宝回调验签。空则拒绝入账 |
| `PAY_ALIPAY_APP_ID` / `PAY_ALIPAY_PRIVATE_KEY` | 管理端打款，放在 `local.admin.env`。缺任一则本次打款结果为失败，冻结额不动 |
| `INVITE_LAND_BASE_URL`                                                  | 落地页公网根，无尾斜杠，如 `https://land.example.com`；拼邀请 URL 与二维码内容 |
| `LEGAL_AGREEMENT_URL` / `LEGAL_PRIVACY_URL`                             | 用户协议、隐私政策 HTTPS；**上线再填**，local 可占位 |


### 9.3 Admin API（`shop-api-admin`）


| 变量                     | 说明                                 |
| ---------------------- | ---------------------------------- |
| `SERVER_PORT`          | 默认 `8082`                          |
| `SSO_ISSUER`           | 如 `https://sso.example.com`        |
| `SSO_CLIENT_ID`        | omino-sso 应用 ID                    |
| `SSO_CLIENT_SECRET`    | 密钥；token 端点用 `client_secret_basic` |
| `SSO_REDIRECT_URI`     | 必须事先在 IdP 注册                       |
| `SSO_LOGIN_URL`        | 门户登录页，如 `http://localhost:5173/login`。登出只作废本应用会话，不结束统一身份会话，并打开该地址所在源的 `/account/apps`。空则不跳到 IdP |
| `ADMIN_SESSION_SECRET` | Cookie 会话加密                        |
| `GOD_VIEW_ENABLED`     | 上帝视角；`local` 默认可 `true`，`prod` 必须 `false` 除非书面开启 |


### 9.4 Agent

`LANGFUSE_*` 本地写在 `local.infra.env`，`local.agent.env` 不重复。


| 变量                      | 说明                                                                  |
| ----------------------- | ------------------------------------------------------------------- |
| `AGENT_INTERNAL_TOKEN`  | 与 Java 相同                                                           |
| `JAVA_APP_INTERNAL_URL` | `shop-api-app` 根，如 `http://127.0.0.1:8181`，Tool 打 `/internal/v1/**` |
| `OPENAI_API_BASE`       | 仅 `local` 且 `llm_model` 没有任何可分流行时的兜底基址，默认 `https://api.deepseek.com` |
| `OPENAI_API_KEY`        | 同上，兜底 Key。有可分流模型后由 Java 按次注入，本变量不参与分流                         |
| `OPENAI_MODEL`          | 兜底模型名，默认 `deepseek-chat`                                           |
| `LANGFUSE_HOST`         | 如 `http://127.0.0.1:3100`                                           |
| `LANGFUSE_PUBLIC_KEY`   |                                                                     |
| `LANGFUSE_SECRET_KEY`   | 密钥                                                                  |
| `LANGFUSE_ENABLED`      | `true`/`false`，`prod` 必须 `true`                                     |


### 9.5 前端 / APP


这些变量不进 `deploy/env`。Vite 应用读本目录 `.env`（模板是 `.env.example`）。Android / iOS 的 `API_BASE` 放在 `apps/android/.env`、`apps/ios/.env`；当前 Gradle 与 Xcode 工程尚未读取。

| 变量                                              | 端                  | 说明                             |
| ----------------------------------------------- | ------------------ | ------------------------------ |
| `VITE_ADMIN_API_BASE`                           | admin-web          | 如 `/api/admin`                 |
| `API_BASE`                                      | Android / iOS      | APP API 根，含 `/api/app`         |
| `VITE_WWW_STORE_IOS` / `VITE_WWW_STORE_ANDROID` | www                | 商店地址                           |
| `VITE_LAND_API_BASE`                            | land               | 可选，公开邀请预览；可空则落地页不调 API         |
| `VITE_APP_SCHEME`                               | land               | 如 `ominoshop://invite` ，拉起 APP |
| `VITE_APP_API_BASE`                             | h5                 | APP API 根，如 `/api/app`         |


---

## 附录 A. 请求链路（实现时不得改泳道）

```
Android/iOS/H5 --Bearer POST /chat/turns--> shop-api-app  (立刻 runId)
Android/iOS/H5 --Bearer GET  /runs/{id}/events SSE--> 泵 --≤100ms--> Redis Stream
                              ^
                              | Worker（任意副本）
                              +-- 调 Agent SSE --> XADD Redis
                              |                |
                              | Tool HTTP      | Langfuse
                              +-- /internal/v1/**
Android/iOS/H5 --详情/转链--> /api/app/v1/goods/detail 与 /convert-link

                    PostgreSQL+pgvector  +  Redis
                              ^
                    Quartz 订单游标同步 --+

Browser --> shop-api-admin --OIDC+PKCE--> omino-sso
Browser --> apps/www（官网） / apps/land /i/{inviteCode}（落地） / apps/h5（消费者 H5）
Admin 上帝视角签发 JWT 后 H5 顶栏常驻标记；prod 默认关闭
Land 拉起 APP 时带 inviteCode；海报由 APP/H5 贴码后分享微信/QQ/相册
```

## 附录 B. 给 Cursor Agent 的工作方式

1. 改任何行为前先改对应权威文档（本文件或 `docs/conversation.md` / `api-*.md` / `data-model.md` / `app-ui.md` / `admin-ui.md`）。
2. Java 按层写：`shop-api-common` 的 dto/entity/mapper 测试 → app/admin 的 service 测试 → web；禁止 Controller 里堆业务。
3. Agent 节点必须可测：意图路由、槽位、指代、Tool 适配器分开；禁止图内直连联盟 SDK。
4. 发现 mall-guide 有而本规范没有的字段：先提案补 `docs/data-model.md`（及对应 api），再实现。
5. 意图枚举与 SSE 事件名以 [`docs/conversation.md`](docs/conversation.md) 为准，禁止 Android、iOS、H5 与 Agent 各猜一套字段。
6. **每次用户发来请求，必须在动手或作答前，向 `[docs/product-input.md](docs/product-input.md)` 主表末尾追加一行**（不得改历史行）。列固定为：`时间`（会话里的本地时间，`YYYY-MM-DD HH:mm`，时区 `Asia/Shanghai`）、`原始输入`（用户原文，可保留换行，禁止改写）、`意图/原因`（一句话：用户要做什么、为何提出）。纯确认（如「好」「继续」）也要记。本条优先于「少改无关 markdown」：这是强制账本，不是可选文档。
7. **按阶段落地**用 Spec Kit，一次只开一个功能。阶段划分、每阶段 `/speckit-specify` 粘贴稿、验收口径见 `[docs/speckit-phases.md](docs/speckit-phases.md)`。禁止把整份本文件一次性丢进 `/speckit-specify`。同一功能不要再跑一遍 OpenSpec。

