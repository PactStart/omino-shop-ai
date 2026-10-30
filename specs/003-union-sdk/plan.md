# Implementation Plan: 四个平台的官方联盟 SDK

**Branch**: `003-union-sdk` | **Date**: 2026-10-30 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-union-sdk/spec.md`

## Summary

把 feat 2 的四个平台适配器从「有密钥也直接失败」换成官方出站。淘宝、京东、拼多多必须用已有联盟数据拿到搜品、详情、转链和至少一笔订单。唯品会达到同样四条，但成功样本只在来源 IP 已加入白名单时采集；白名单外的失败是 `50010`，不算通过。

官方包先复制到 `services/java/sdks`，两条命令分别装进本机 Maven 和上传私有仓库。调用只留在 `adapter.platform`。入账、游标事务和转链授权门闩保持 feat 2。选择见 [research.md](research.md)。

## Technical Context

**Language/Version**: Java 21（Temurin `21.0.8`），Maven `3.9.9`

**Primary Dependencies**: 已锁定的 Spring Boot `3.4.11`。本功能新增五份厂商包，引入前登记到 `AGENTS.md` §2.1：`com.taobao:taobao-sdk-java:20250925`（含 sources）、`com.jd:open-api-sdk:20250925`、`com.pdd:pop-sdk:1.10.16`、`com.vip:vop-sdk:20250214094310`、`com.vip:osp-sdk:20250214094310`。它们不在 Maven Central。只用 research.md 列出的搜品、详情、转链、订单增量请求。依赖只进 `shop-api-app`。

**Storage**: 不新增表。主库仍是 PostgreSQL 16 + pgvector。订单水位仍是 `order_sync_cursor` 的两列时间。Redis 不参与联盟出站。

**Testing**: JUnit 5 覆盖密钥为空时的 `50010`、零出站、秘密不泄露，以及既有的一次入账。成功样本按 [quickstart.md](quickstart.md) 用测试密钥打官方，不放进每次 CI。唯品会的成功样本在白名单 IP 上采集。

**Target Platform**: APP `:8181`，前缀 `/api/app`。订单同步在 APP 进程的 Quartz。Admin 不调用联盟 SDK。

**Project Type**: 在现有 APP 进程里替换平台适配层，加一组本机 Maven 命令。

**Performance Goals**: 单次官方调用 10 秒超时，超时为 `50010`。不另定吞吐指标。订单调度间隔仍是数据模型里的默认 5 分钟。

**Constraints**: 未授权转链先返回 `40301` / `NEED_AUTH`，不出站。密钥为空返回 `50010` 且不出站，水位不动。响应、日志和 `last_error` 不含密钥、PID、relationId。私服地址和口令只来自操作者 `settings.xml`。不复制参考仓库的源码和脚本。

**Scale/Scope**: 四个平台、五份 jar、两类命令。不包含页面、对话图、美团、大淘客、新平台码、新表。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 计划前 | 设计后 |
| --- | --- | --- |
| I. 一套 Java，两个产出物 | 通过。SDK 只进 app 进程 | 通过。Admin 的 POM 不引用这五份坐标 |
| II. mall-guide 只读 | 通过。只复制厂商 jar 与随包 pom，不复制其 Java、表名和脚本 | 通过。research.md 只记下官方请求名，适配层在本仓库重写 |
| III. 管理后台只走 omino-sso | 通过。本功能不碰登录 | 通过。没有新的管理端身份 |
| IV. 密钥与转链只在 Java | 通过。密钥仍是环境变量。未授权先 40301 | 通过。转链仍经现有门闩，官方调用在适配层。日志用固定失败文案 |
| V. 主库唯一，先改文档再实现 | 通过。不新增表，不改 HTTP 路径 | 通过。data-model.md 只描述既有列的填法。没有第二套契约 |
| VI. 先检索再实现 | 例外，见 Complexity Tracking。规格已点名五份非 Central 的厂商包 | 通过同一例外。用哪一部分已写入 research.md。实现前登记 §2.1，并核对依赖树不抬高已锁定版本 |
| VII–XI. 实体、外键、枚举、OpenAPI、注释 | 通过。不新增实体和接口 | 通过。现有 Controller 的注解保持。新增类按宪章写 Javadoc |

无未解决的 NEEDS CLARIFICATION。

## Project Structure

### Documentation (this feature)

```text
specs/003-union-sdk/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── sdk-install.md
│   └── outbound.md
└── tasks.md              # 由 /speckit-tasks 生成，本命令不创建
```

已有 HTTP 不在 `contracts/` 里再写一遍。

### Source Code (repository root)

```text
services/java/
├── pom.xml                         # dependencyManagement 增加五份坐标
├── sdks/                           # jar、随包 pom、淘宝 sources、两条命令
├── shop-api-app/
│   └── src/main/java/com/omino/shopai/app/
│       ├── adapter/platform/       # 唯一允许调用官方包的包
│       │   ├── PlatformGoodsClient.java   # 增加转链
│       │   ├── *GoodsClient.java          # 替换直接失败的壳
│       │   └── *OrderSource.java          # 替换始终为空的订单来源
│       ├── service/goods/           # 授权门闩之后调用适配层转链
│       ├── service/order/           # 事务与入账不改规则
│       └── job/OrderSyncJob.java    # 一个平台失败不挡住其余平台
└── shop-api-admin/                  # 不依赖联盟 SDK
```

**Structure Decision**: 目录按 `AGENTS.md` §3。web 不调 SDK。service 只编排适配层。`IncomingOrder` 若被适配层填入，放到 common 的 dto 或适配包，避免适配层依赖 service。订单写入、锁钱包和游标前进仍在 `OrderSyncService` 的同一事务。

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
| --- | --- | --- |
| VI. 厂商 SDK 的许可证不是 MIT / Apache-2.0 / BSD | 规格点名的五份官方包不在 Maven Central，签名与报文由厂商包完成 | 手写签名违反「先检索再实现」。另找社区封装会改坐标，并多一层依赖 |

## Phase 0

研究结论在 [research.md](research.md)。没有留下待澄清项。

## Phase 1

- 数据：见 [data-model.md](data-model.md)。无迁移。
- 契约：见 [contracts/sdk-install.md](contracts/sdk-install.md) 与 [contracts/outbound.md](contracts/outbound.md)。
- 验收步骤：见 [quickstart.md](quickstart.md)。

设计完成后已按上表重查宪章。原则 I 到 V 以及 VII 到 XI 通过。原则 VI 的许可证例外仍只覆盖这五份坐标；实现时若依赖树抬高已锁定版本，停止并改用规格允许的同一坐标的安装方式，不换包。
