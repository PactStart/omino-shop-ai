# Implementation Plan: Java 双产出物的全部 HTTP、表和同步

**Branch**: `002-java-api` | **Date**: 2026-10-29 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-java-api/spec.md`

## Summary

在 feat 1 的两个 Java 进程上，按用户故事 1 到 11 做完消费者、运营和内部工具的 HTTP、表和同步。页面和对话图不做。`test` 与 `prod` 的 Worker 调用 Agent；调不通时写入事件 `error`，`code` 为 `50020`。样例事件只在测试双写或 `local`。

施工仍按编号从故事 1 开始。对购物用户可演示的停点是故事 3（详情与转链门闩）。资金规则的停点是故事 5（入账一次和提现）。故事 1 只是第一刀，不是对用户的里程碑。

新工具只加三件，都先写入 `AGENTS.md` §2.1 再进 POM：MyBatis-Plus 同版本的 XML 生成器、微信支付官方库、支付宝官方库。管理端登录沿用已锁定的 OAuth2 Client，不升 Boot。提现和管理端会话的路径先补进权威契约，再写代码。选择见 [research.md](research.md)。

## Technical Context

**Language/Version**: Java 21（Temurin `21.0.8`），Maven `3.9.9`

**Primary Dependencies**: 已锁定的 Spring Boot `3.4.11`、MyBatis-Plus `3.5.9`、Flyway `11.3.1`、Spring Security OAuth2 Client。本功能新增，引入前登记到 `AGENTS.md` §2.1：`mybatis-plus-generator` `3.5.9`、`wechatpay-java` `0.2.17`、`alipay-sdk-java` `4.40.1044.ALL`。不另锁一套已有版本。

**Storage**: 主库 PostgreSQL 16 + pgvector。Redis 7.4 存对话事件流和取消键。Nacos 只提供已有热配置项。不用 MySQL。

**Testing**: JUnit 5。每个用户故事至少一条测试。资金、验签和打款用测试密钥或测试打款端口，不连真实商户。钱包余额变更至少有一条两个事务交错的测试。生成器在改表时本地跑一次，XML 入库；CI 只加载已提交的 XML，不重跑生成器。curl 步骤见 [quickstart.md](quickstart.md)。依赖树在把三个新坐标写进 POM 时执行，通过之前不算版本锁定已经完成。

**Target Platform**: 本地 APP `:8181` 前缀 `/api/app`，Admin `:8182` 前缀 `/api/admin`。`/internal/v1` 只挂在 APP 进程。

**Project Type**: 两个 HTTP 服务进程，一套代码。

**Performance Goals**: 对话订阅从 Redis 读出的间隔不超过 100 毫秒。终态后该轮 Stream 再保留 30 分钟，与 [`docs/conversation.md`](../../docs/conversation.md) 一致。未结束的轮由 Worker 按该契约超时写成 `error`。没有吞吐指标，也不另定一套长度上限。

**Constraints**: 无凭证为 HTTP `401` 且 `code` 为 `40100`。未授权转链为 `40301` / `NEED_AUTH`。完成单只入账一次。打款失败保持冻结。密钥、推广位标识、完整收款账号不出现在响应和日志里。APP 与 Admin 互不依赖。

**Scale/Scope**: 权威数据模型中的业务表，加上提现的 `paid` / `rejected` 和两列打款字段。11 个用户故事按编号施工。不包含页面、对话图、美团、上级第二笔分成。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 计划前 | 设计后 |
| --- | --- | --- |
| I. 一套 Java，两个产出物 | 通过。业务仍在 app 与 admin，公共实体在 common | 通过。内部工具只挂 app。提现的两边路径分属两个契约文件 |
| II. mall-guide 只读 | 通过。生成器从本库表结构产出 XML，不读取参考仓库 | 通过。数据模型只补本仓库提现状态 |
| III. 管理后台只走 omino-sso | 通过。不建密码表，沿用已锁定的 OAuth2 Client | 通过。见 contracts/admin-session.md，cookie 不叫 `sid`，令牌不进浏览器 |
| IV. 密钥与转链只在 Java，对话链路固定 | 通过。验签和打款只在 Java。停止仍走 Redis | 通过。`test`/`prod` 的 Worker 调用 Agent，失败写事件 `error` 且 `code` 为 `50020`。样例事件只在测试双写或 `local`。打款客户端在 Admin，回调验签在 APP，两边不互引 |
| V. 主库唯一，先改文档再实现 | 通过。主库仍是 PostgreSQL。新路径先写入权威契约 | 通过。contracts 写明实现前要补的文档，没有第二套列定义 |
| VI. 先检索再实现 | 通过。三件新依赖都是 Apache-2.0，且不抬高已锁定版本 | 设计选择通过，见 research.md。Boot 留在 `3.4.11`。`.ALL` 支付宝包的依赖树在写入 POM 时才能算通过；若抬高已锁定版本，改用仍为 Apache-2.0 且不抬版本的最新 `4.40.x` |

无未解决的 NEEDS CLARIFICATION。无需要豁免的宪章例外。

## Project Structure

### Documentation (this feature)

```text
specs/002-java-api/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── admin-session.md
│   └── withdrawal.md
└── tasks.md              # 由 /speckit-tasks 生成，本命令不创建
```

已有 HTTP 不在 `contracts/` 里再写一遍。实现以 `docs/api-app.md`、`docs/api-admin.md`、`docs/api-internal.md`、`docs/conversation.md` 为准。

### Source Code (repository root)

```text
services/java/
├── shop-api-common/
│   └── src/main/java/com/omino/shopai/
│       ├── entity/          # 与 docs/data-model.md 一一对应
│       ├── mapper/          # 生成的 Mapper 接口；改表时本地生成后提交，CI 不重跑
│       ├── dto/
│       └── support/withdrawal/  # 状态转移纯函数。不打开事务，不依赖 Servlet
│   └── src/main/resources/mapper/   # 生成的 XML 与手写 *.custom.xml
├── shop-api-app/
│   └── src/main/java/com/omino/shopai/app/
│       ├── web/app/         # /api/app/v1
│       ├── web/internal/    # /internal/v1，基础阶段就校验内部令牌
│       ├── service/         # 提现只做申请。入账与钱包购积分先锁钱包行
│       ├── adapter/platform/   # 仅此包调四个联盟客户端
│       ├── adapter/pay/        # 微信与支付宝回调验签。不放单笔转账
│       └── job/                # 订单游标同步
└── shop-api-admin/
    └── src/main/java/com/omino/shopai/admin/
        ├── adapter/pay/     # 支付宝单笔转账。幂等号是 withdrawal.id
        ├── web/
        ├── security/        # OAuth2 BFF 与 sid_admin
        └── service/         # 提现打款与拒绝。调用 common 的纯函数
```

**Structure Decision**: 目录按 `AGENTS.md` §3。web 不写 SQL、不调联盟 SDK。service 不依赖 Servlet 类型。生成器不产出 Service。APP 与 Admin 只共用 common，互不依赖。支付宝坐标由 parent 管理：APP 用它验签，Admin 用它转账。微信验签库只进 APP。迁移只向前，不写 down，回退靠恢复备份。

## Complexity Tracking

本功能无需要豁免的宪章例外。样例对话不是例外：`test` 与 `prod` 不注册样例 Worker。

## Phase 0

研究结论在 [research.md](research.md)。没有留下待澄清项。

## Phase 1

- 数据：见 [data-model.md](data-model.md)。表结构仍以 `docs/data-model.md` 为准。这里只加提现状态，以及成功后才写入的通道单号列。发给通道的幂等号是 `withdrawal.id`，不存在那一列里。
- 契约：已有接口不复制。新增见 [contracts/admin-session.md](contracts/admin-session.md) 与 [contracts/withdrawal.md](contracts/withdrawal.md)。
- 验收步骤：见 [quickstart.md](quickstart.md)。

设计完成后已按上表重查宪章。原则 I 到 V 通过。原则 VI 的库选择通过。2026-10-29 写入 POM 后的依赖树已通过：Boot 仍是 `3.4.11`，Jackson 仍是 `2.18.4`，微信库只在 `shop-api-app`。支付宝包传递带入 `fastjson`，业务代码不得调用。
