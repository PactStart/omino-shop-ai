---

description: "Task list for 003-union-sdk"
---

# Tasks: 四个平台的官方联盟 SDK

**Input**: Design documents from `/specs/003-union-sdk/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: 规格的独立测试和计划里的 JUnit 要求都算明确要求。每个故事先写会失败的测试，再实现。带测试密钥的成功样本放在收尾的 quickstart，不进每次 CI。

**Organization**: 按用户故事 1 到 4 的编号施工。故事 1 未通过，不开始出站。故事 2、3、4 改同一批适配器文件，不并行。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件，且不依赖未完成任务）
- **[Story]**: 用户故事标签，仅用户故事阶段使用

## Path Conventions

- Java：`services/java/shop-api-app`、`services/java/shop-api-common`、`services/java/pom.xml`
- 官方包目录：`services/java/sdks`
- 适配层：`services/java/shop-api-app/src/main/java/com/omino/shopai/app/adapter/platform/`
- 权威文档：`AGENTS.md` §2.1、`docs/api-app.md`、`docs/data-model.md`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 先把五份坐标写入权威技术栈表，再让构建依赖它们

- [X] T001 在 `AGENTS.md` §2.1 登记五份坐标与版本：`com.taobao:taobao-sdk-java:20250925`（含 sources jar）、`com.jd:open-api-sdk:20250925`、`com.pdd:pop-sdk:1.10.16`、`com.vip:vop-sdk:20250214094310`、`com.vip:osp-sdk:20250214094310`。写明只进 `shop-api-app`，用哪一部分与不用哪一部分按 `specs/003-union-sdk/research.md` 的表。写明许可证不是 MIT / Apache-2.0 / BSD，这是规格点名的例外。不换坐标

**Checkpoint**: 技术栈表已有五份坐标。仓库里还没有 jar，POM 也还没依赖它们

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 密钥检查在发任何官方请求之前失败。出站故事共用这一道门闩

**⚠️ CRITICAL**: 用户故事开始前必须完成本阶段

- [X] T002 先写失败测试 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/adapter/UpstreamTest.java`：任一密钥为 null 或空白时 `Upstream.call` 抛出 `PlatformCallException` 且不打开网络；全部非空时方法返回，仍然不打开网络
- [X] T003 改 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/adapter/platform/Upstream.java`，使 T002 通过。现有四个 `*GoodsClient` 在 `Upstream.call` 之后仍抛 `PlatformCallException`，因此密钥齐全时搜品暂时仍是 `50010`。本阶段不调用官方包

**Checkpoint**: 空密钥在适配层入口就被拒绝。四个客户端仍是失败壳。还没有 jar

---

## Phase 3: User Story 1 - 官方包能装进本机并传到私有仓库 (Priority: P1) 🎯 MVP

**Goal**: 五份 jar 在 `services/java/sdks`，安装命令装进本机 Maven，上传命令从 `settings.xml` 读地址和口令，APP 模块能编译，Admin 不依赖这些包

**Independent Test**: 目录里有五份坐标的 jar，淘宝另有 sources jar。安装命令成功。上传脚本和仓库文件没有私服口令。`shop-api-app` 能编译，`shop-api-admin` 的 POM 没有这五份坐标

### Tests for User Story 1

- [X] T004 [US1] 先写失败测试 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/adapter/SdkArtifactsTest.java`：`services/java/sdks` 含淘宝、京东、拼多多、唯品会 vop、唯品会 osp 的 jar，以及淘宝 sources jar；该目录没有 `.java` 文件，也没有参考仓库的 `install-*.sh` / `deploy-*.sh`

### Implementation for User Story 1

- [X] T005 [US1] 从参考仓库 `sdks` 只复制 jar、随包 pom 和淘宝 sources jar 到 `services/java/sdks`，使 T004 通过。不复制 Java 源码、封装类、调用代码和参考仓库的脚本。京东与拼多多保留随包 pom；唯品会两份和淘宝没有随包 pom 时不补造业务 pom
- [X] T006 [P] [US1] 新增 `services/java/sdks/install-local.sh`，对五份坐标执行 `mvn install:install-file`。淘宝 sources jar 使用 `classifier=sources`。没有随包 pom 的坐标用 groupId、artifactId、version 参数发布
- [X] T007 [P] [US1] 新增 `services/java/sdks/deploy-private.sh`，对同样五份执行 `mvn deploy:deploy-file`。地址只从操作者 `settings.xml` 已激活属性 `omino.sdk.repo.url` 读取，认证只用 server id `omino-shop-sdk`。属性或 server 缺失时非零退出。脚本内容不包含仓库 URL、口令、联盟密钥
- [X] T008 [US1] 把五份坐标写入 `services/java/pom.xml` 的 `dependencyManagement`，并只在 `services/java/shop-api-app/pom.xml` 声明依赖。`services/java/shop-api-admin/pom.xml` 不得出现这些坐标。先运行 T006 再编译。确认没有抬高 Boot `3.4.11` 及已锁定的 Jackson、MyBatis-Plus。厂商包若传递带入 Fastjson，不在业务代码里调用

**Checkpoint**: 本机可以编译带官方包的 APP。搜品仍因客户端里的失败壳返回 `50010`。上传命令在未配置 settings 时失败退出

---

## Phase 4: User Story 2 - 有密钥时能搜到商品并看详情 (Priority: P1)

**Goal**: 淘宝、京东、拼多多在测试密钥下搜品至少一条、详情一条。唯品会在白名单 IP 上同样成功。密钥为空时 `50010` 且不出站。销量未知保持空

**Independent Test**: 清空任一必需密钥后搜品和详情为 `50010`，出站次数为 0。官方没给销量或评价数时这两项为 null。成功样本按 quickstart，不在本阶段用 CI 打外网

### Tests for User Story 2

- [X] T009 [P] [US2] 先写失败测试 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/adapter/PlatformClientTest.java`：四个平台任一必需密钥为空时，搜品和详情抛出平台失败且不创建官方客户端。更新现有「密钥齐全也失败」的断言，密钥齐全不再被 `Upstream` 拒绝
- [X] T010 [P] [US2] 先写失败测试 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/adapter/SalesCountMappingTest.java`：官方没给或无法解析时 `sales_count` 与 `review_count` 为空；官方给出的数字按原值写入，包括 0

### Implementation for User Story 2

- [X] T011 [US2] 在 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/adapter/platform/TaobaoGoodsClient.java` 实现搜品 `TbkDgMaterialOptionalUpgradeRequest` 与详情 `TbkItemInfoUpgradeGetRequest`。单次调用超时 10 秒。官方拒绝或超时变成 `PlatformCallException`，不把官方原文放进异常消息。使 T010 的映射规则在该客户端生效
- [X] T012 [P] [US2] 在 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/adapter/platform/JdGoodsClient.java` 用 `UnionOpenGoodsQueryRequest` 实现搜品和按商品号详情，超时与失败文案同 T011
- [X] T013 [P] [US2] 在 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/adapter/platform/PddGoodsClient.java` 用 `PddDdkGoodsSearchRequest` 与 `PddDdkGoodsDetailRequest` 实现搜品和详情，超时与失败文案同 T011
- [X] T014 [P] [US2] 在 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/adapter/platform/VipGoodsClient.java` 用 `vop-sdk` 的商品查询和按商品号取详情实现搜品和详情，`osp-sdk` 只作客户端运行时。超时与失败文案同 T011。不调用相似推荐和用户推荐

**Checkpoint**: 空密钥的搜品和详情在 CI 中为 `50010`。四个客户端不再在密钥齐全时无条件抛失败。真实成功样本留到收尾

---

## Phase 5: User Story 3 - 已授权后才能把链接换成可购买链接 (Priority: P1)

**Goal**: 未授权转链仍是 `40301` / `NEED_AUTH` 且不调用官方。已授权且密钥齐全时返回可购买链接并写点击。已授权但密钥为空是 `50010` 且不出站

**Independent Test**: 未授权用户即使应用密钥配齐也得到 `40301`，官方转链次数为 0。已授权的成功转链留下 `click_event`。响应无 PID、无密钥

### Tests for User Story 3

- [X] T015 [US3] 先写失败测试 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/goods/GoodsConvertGateTest.java`：未授权时 `GoodsConvertLinkService` 返回 `40301` 且 `error.reason` 为 `NEED_AUTH`，转链方法不被调用；已授权但密钥为空时 `50010` 且不出站；已授权且签发成功后才写点击

### Implementation for User Story 3

- [X] T016 [US3] 在 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/adapter/platform/PlatformGoodsClient.java` 增加转链方法。改 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/goods/ServerLinkIssuer.java`，在授权检查之后调用该方法，不再用商品页拼接 `openapp.{platform}://item/`。未安装 APP 时的链接形态仍按 `docs/api-app.md`
- [X] T017 [P] [US3] 在 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/adapter/platform/TaobaoGoodsClient.java` 用 `TbkDgGeneralLinkConvertRequest` 实现转链。响应对象不包含 PID、relationId、密钥
- [X] T018 [P] [US3] 在 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/adapter/platform/JdGoodsClient.java` 用 `UnionOpenPromotionBysubunionidGetRequest` 实现转链，响应约束同 T017
- [X] T019 [P] [US3] 在 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/adapter/platform/PddGoodsClient.java` 用 `PddDdkGoodsPromotionUrlGenerateRequest` 实现转链，响应约束同 T017
- [X] T020 [P] [US3] 在 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/adapter/platform/VipGoodsClient.java` 用按商品号生成链接的官方方法实现转链，响应约束同 T017。不调用推广位生成和频道链接

**Checkpoint**: 未授权转链在 CI 中为 `40301`。官方可购买链接的成功样本留到收尾

---

## Phase 6: User Story 4 - 真实订单仍按原游标入账一次 (Priority: P2)

**Goal**: 订单增量打到官方，至少写入一笔订单才算正向通过。写入、入账和水位前进仍在同一事务。密钥为空或官方拒绝时两列水位不动。重复同步不双写返佣

**Independent Test**: 淘宝、京东、拼多多在测试密钥下各至少写入一笔订单；唯品会这笔在白名单 IP 上验收。再同步同一笔已完成且待结算的订单，返佣流水仍为 1 条。密钥为空时 `50010`、不出站、水位不变

### Tests for User Story 4

- [X] T021 [US4] 先写失败测试 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/order/OrderSyncMissingKeyTest.java`：某平台密钥为空时拉取抛平台失败，`order_sync_cursor.last_sync_create_time` 与 `last_sync_update_time` 都不变，且不创建官方客户端。一个平台失败不阻止其余平台被调用。`last_error` 为固定文案，不含密钥、PID、relationId 和官方原文

### Implementation for User Story 4

- [X] T022 [US4] 把 `IncomingOrder` 移到 `services/java/shop-api-common/src/main/java/com/omino/shopai/dto/order/IncomingOrder.java`。把 `PlatformOrderSource` 移到 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/adapter/platform/PlatformOrderSource.java`。更新 `OrderSyncService.java` 与 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/order/OrderSettleOnceTest.java` 的引用。适配层不得依赖 `app.service`
- [X] T023 [US4] 新增 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/adapter/platform/SdkPlatformOrderSource.java` 作为唯一的 `PlatformOrderSource`。删除 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/order/EmptyPlatformOrderSource.java` 的组件注册。密钥在创建官方客户端之前检查。拉取失败不得进入 `OrderSyncService` 的写入事务
- [X] T024 [P] [US4] 在 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/adapter/platform/TaobaoGoodsClient.java` 用 `TbkOrderDetailsGetRequest` 按 `last_sync_create_time` 与 `last_sync_update_time` 两条水位拉单。`order_status` 只写成待支付、待发货、已发货、已完成、已取消、未知；对不上的原码写成未知
- [X] T025 [P] [US4] 在 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/adapter/platform/JdGoodsClient.java` 用 `UnionOpenOrderRowQueryRequest` 按两列水位拉单，状态映射同 T024
- [X] T026 [P] [US4] 在 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/adapter/platform/PddGoodsClient.java` 用 `PddDdkOrderListIncrementGetRequest` 拉单，状态映射同 T024
- [X] T027 [P] [US4] 在 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/adapter/platform/VipGoodsClient.java` 用 `orderList` 拉单，状态映射同 T024。不调用退款单接口
- [X] T028 [US4] 改 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/job/OrderSyncJob.java` 与 `services/java/shop-api-app/src/main/java/com/omino/shopai/app/service/order/OrderSyncService.java`：单个平台失败不影响其余平台；失败时 `last_error` 写成固定文案「上游失败」，不修改两列水位；成功推进水位时清空 `last_error`。入账条件、一次流水、不给上级写第二笔保持现有 `OrderSyncService`。官方正常返回的空列表算成功拉取并前进水位，但不算正向验收

**Checkpoint**: 空密钥不同步水位。`OrderSettleOnceTest` 仍证明同一笔订单只入账一次。真实订单样本留到收尾

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 确认依赖边界，并采集四个平台的成功样本

- [X] T029 [P] 检查 `services/java/shop-api-admin/pom.xml` 与 Admin 源码没有五份联盟坐标，也没有 `com.taobao`、`com.jd.open`、`com.pdd.pop`、`com.vip` 的 import
- [ ] T030 按 `specs/003-union-sdk/quickstart.md` 执行：第 2 节空密钥与未授权转链必须通过；第 3 节用 `make accept` 采集搜品、详情、转链，不拉订单。淘宝、京东、拼多多各有一条搜品、一条详情、一条可购买链接。唯品会的三条只在来源 IP 已加入白名单时采集。白名单外的 `50010` 不算通过
- [X] T031 再跑 `services/java/shop-api-app/src/test/java/com/omino/shopai/app/order/OrderSettleOnceTest.java`，确认真实出站接入后同一笔已完成且待结算的订单连续同步两次，返佣流水仍为 1 条

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无前置，先改 `AGENTS.md`
- **Foundational (Phase 2)**: 依赖 Phase 1 的登记完成，并挡住全部出站故事
- **User Story 1 (Phase 3)**: 依赖 Phase 2。未通过不得开始故事 2
- **User Story 2 (Phase 4)**: 依赖故事 1 的 jar 与 POM
- **User Story 3 (Phase 5)**: 依赖故事 2，因为转链写在同一批 `*GoodsClient.java`
- **User Story 4 (Phase 6)**: 依赖故事 3，因为拉单仍写在同一批客户端
- **Polish (Phase 7)**: 依赖故事 1 到 4

### User Story Dependencies

- **User Story 1 (P1)**: 只依赖密钥门闩。不调用官方接口
- **User Story 2 (P1)**: 依赖故事 1。不依赖转链和订单
- **User Story 3 (P1)**: 依赖故事 2 的客户端文件，授权门闩沿用现有 `GoodsConvertLinkService`
- **User Story 4 (P2)**: 依赖故事 3 的客户端文件。入账事务沿用现有 `OrderSyncService`

### Within Each User Story

- 先写测试并确认失败，再实现
- 故事 2、3、4 里四个平台的客户端方法在接口就绪后可以并行
- 四个故事之间不并行

### Parallel Opportunities

- T006 与 T007 可并行
- T009 与 T010 可并行
- T012、T013、T014 在 T011 之后可并行
- T017、T018、T019、T020 在 T016 之后可并行
- T024、T025、T026、T027 在 T023 之后可并行
- T029 可与 T031 并行。T030 的唯品会样本要等白名单来源

---

## Parallel Example: User Story 2

```bash
# T011 完成后同时改三个平台的搜品和详情：
Task: "JdGoodsClient 用 UnionOpenGoodsQueryRequest"
Task: "PddGoodsClient 用 PddDdkGoodsSearchRequest 与 PddDdkGoodsDetailRequest"
Task: "VipGoodsClient 用 vop-sdk 商品查询和按商品号详情"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1：坐标写入 `AGENTS.md`
2. 完成 Phase 2：空密钥在 `Upstream` 被拒绝
3. 完成 Phase 3：jar、两条命令、APP 能编译
4. **停下来验证**：T004 通过，Admin 不依赖官方包，上传脚本里没有口令

### Incremental Delivery

1. 故事 1 通过后才替换搜品和详情
2. 故事 2 的空密钥测试通过后才做转链
3. 故事 3 的 `40301` 测试通过后才做订单增量
4. 收尾用 quickstart 采集成功样本。唯品会不在白名单上的失败不能代替成功样本

### Parallel Team Strategy

故事之间顺序固定。只有同一故事里的四个平台文件，在该故事的接口改完之后并行。

---

## Notes

- 不新增表，不改 `docs/api-app.md` 的路径
- 不复制参考仓库的 Java 和脚本
- 官方正常返回的空列表不是 `50010`，但也不能当作 SC-003 的成功样本
- 成功样本需要测试密钥。唯品会还需要白名单 IP
