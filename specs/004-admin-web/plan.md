# Implementation Plan: 管理后台页面

**Branch**: `004-admin-web` | **Date**: 2026-10-31 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/004-admin-web/spec.md`

## Summary

运营用现有身份服务登录管理后台，按 [`docs/admin-ui.md`](../../docs/admin-ui.md) 操作每一页。页面需要而管理接口还做不到的能力先补进 [`docs/api-admin.md`](../../docs/api-admin.md) 再实现，不留空壳。推荐问题补上列表和修改，保存后能再查到。订单同步重试仍调用 APP 进程里已有的增量，Admin 不引用联盟 SDK。

选择见 [research.md](research.md)。

## Technical Context

**Language/Version**: 管理接口 Java 21（Temurin `21.0.8`），Maven `3.9.9`。页面 TypeScript `5.7.2`，Vue `3.5.13`。

**Primary Dependencies**: 已锁定的 Spring Boot `3.4.11`、Element Plus `2.9.1`、Pinia `2.3.0`、Vue Router `4.5.0`、axios `1.7.9`、Vite `6.3.5`。二维码只加 `qrcode`，引入前把精确版本写入 `AGENTS.md` §2.4，只用 Canvas 输出。不引入 `oidc-client-ts`。

**Storage**: 不新增表。主库仍是 PostgreSQL 16。`kb_article.status` 的文档注释在实现前改成与现有枚举一致（`published` / `retired`），不迁移数据。

**Testing**: 新的管理用例用 JUnit 5：推荐问题读回与第 13 条上架、工单回复不关单、知识库下架后检索不命中、同步重试拒绝第五个平台且审计不含密钥。页面按 [quickstart.md](quickstart.md) 在浏览器里走一遍。不为本功能另加前端测试框架。

**Target Platform**: 管理前端 `http://127.0.0.1:5183`，Admin API `:8182` 前缀 `/api/admin`。同步执行仍在 APP `:8181`。

**Project Type**: 现有 Vite 后台补齐页面，并在 `shop-api-admin` 补齐页面所依赖的 HTTP。两个 Java 产出物不互相 import。

**Performance Goals**: 列表分页，默认每页 20。工作台只返回三个计数，不拉全表到浏览器再数。不另定吞吐指标。

**Constraints**: 浏览器不持有 access_token，没有账号密码页。用户视角签发成功只提示成功，不展示凭证，不打开消费者页面。模型密钥只写不回显。响应和表格没有联盟 PID 和完整收款账号。提示词不能增删 key。推荐问题上架最多 12。海报不向服务端要合成图。不做消费者 H5、官网、落地页。

**Scale/Scope**: 信息架构里的全部菜单和详情页。新增的管理路径见 [contracts/admin-additions.md](contracts/admin-additions.md)。契约里已有但未实现的方法按原文档补齐，不在契约目录再抄一遍。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 计划前 | 设计后 |
| --- | --- | --- |
| I. 一套 Java，两个产出物 | 通过。新 HTTP 在 admin。同步执行留在 app，经内部 HTTP 触发，两边不互相 import | 通过。内部路径不进入 Agent 工具白名单，SDK 仍只在 app |
| II. mall-guide 只读 | 通过。不读参考仓库来做后台 | 通过。没有复制其页面或接口 |
| III. 管理后台只走 omino-sso | 通过。沿用现有授权码交换，不新建密码 | 通过。前端不换 token。回调改回前端源 |
| IV. 密钥与转链只在 Java | 通过。页面不接触联盟密钥和模型明文 | 通过。重试的失败文案沿用现有固定句。审计 detail 不含密钥 |
| V. 主库唯一，先改文档再实现 | 通过。不新增表。先改 api-admin，知识库状态注释对齐枚举 | 通过。data-model.md 只描述既有表的读写。没有第二套字段 |
| VI. 先检索再实现 | 通过。登录用已有 OIDC 客户端。二维码用现成库并先锁版本，不手写编码 | 通过。research.md 写明只用该库的 Canvas 输出 |
| VII–XI. 实体、外键、枚举、OpenAPI、注释 | 通过。不新建实体。工单和知识库用已有枚举。新映射补 `@Operation` 与 Javadoc | 通过。分页类型挪到 common，仍是一个 `PageDto` |

无未解决的 NEEDS CLARIFICATION。

## Project Structure

### Documentation (this feature)

```text
specs/004-admin-web/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── admin-additions.md
└── tasks.md              # 由 /speckit-tasks 生成，本命令不创建
```

已在 `docs/api-admin.md` 里的路径不在 `contracts/` 再写一遍。

### Source Code (repository root)

```text
apps/admin-web/
├── vite.config.ts                 # 把 /api/admin 反代到 8182
└── src/
    ├── api/http.ts                # axios，withCredentials，相对 /api/admin
    ├── router/index.ts            # 路由 ID 对齐 admin-ui
    ├── layouts/AdminShell.vue     # 左栏、顶栏、登出
    └── views/                     # 一页一个视图，含详情

services/java/
├── shop-api-common/
│   └── dto/PageDto.java           # 从 app 模块移入
├── shop-api-admin/
│   └── src/main/java/com/omino/shopai/admin/
│       ├── web/                   # 新映射与补齐的 GET/PATCH
│       └── service/               # 一个领域一个用例，不按路由再拆
└── shop-api-app/
    └── web/internal/              # 仅 POST /internal/v1/order-sync/{platform}
```

**Structure Decision**: 目录按 `AGENTS.md` §3。页面只打 Admin API。web 不写 SQL。工单变更、调账、同步重试放在 admin 的 service，因为它们要保证事务或审计。只读列表可以留在现有的目录式查询里，但返回分页，不再返回裸列表冒充分页。APP 的内部路径只包一层现有 `OrderSyncService.sync`，不复制入账代码。

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
| --- | --- | --- |
| 无 |  |  |

Admin 调用 APP 的内部路径是进程边界，不是第三个产出物，也不是两个模块互相 import。

## Phase 0

研究结论在 [research.md](research.md)。没有留下待澄清项。

## Phase 1

- 数据：见 [data-model.md](data-model.md)。无迁移。
- 契约：见 [contracts/admin-additions.md](contracts/admin-additions.md)。实现前写入 `docs/api-admin.md`。
- 验收步骤：见 [quickstart.md](quickstart.md)。

设计完成后已按上表重查宪章。原则 I 到 XI 通过。二维码库在第一次加入 `package.json` 之前必须已经写进 `AGENTS.md` §2.4；未写入则不要安装。
