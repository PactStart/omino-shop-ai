# SDD 工具用法（本仓库）

本仓库同时装了三套规格驱动相关工具。它们职责不同，**不要叠着用同一件事两遍**。

| 文件 | 作用 |
|---|---|
| [`AGENTS.md`](../AGENTS.md) | 索引 + 技术栈、目录、约束、信封、环境变量 |
| [`conversation.md`](./conversation.md) | 意图、图、记忆、SSE |
| [`api-app.md`](./api-app.md) / [`api-admin.md`](./api-admin.md) / [`api-internal.md`](./api-internal.md) | HTTP 契约 |
| [`data-model.md`](./data-model.md) | 表结构 |
| 本文 | 怎么用 Superpowers / OpenSpec / Spec Kit，以及何时选哪套 |
| [`product-input.md`](./product-input.md) | 每次请求的账本（时间 / 原文 / 意图） |
| [`agent.md`](./agent.md) | Python Agent 运行时说明（契约以 conversation / api-internal / data-model 为准） |
| [`speckit-phases.md`](./speckit-phases.md) | Spec Kit 分阶段：一次一个功能、粘贴稿、验收；不替代 `AGENTS.md` |
| [`app-ui.md`](./app-ui.md) | 消费者页面 | 
| [`admin-ui.md`](./admin-ui.md) | 管理后台页面 |

实现以 `AGENTS.md` 与 §4 拆出文档为准。下面三套工具负责「怎么想、怎么拆、怎么落地」，不替代契约。

---

## 一张图：怎么选

```
模糊想法 / 还没想清楚
    → Superpowers brainstorming（或 OpenSpec explore）
    → 先改 AGENTS.md / 记下 product-input.md

一条可交付的功能（登录、对话一轮、转链…）
    → 默认 Spec Kit：specify → plan → tasks → implement
    → 或 OpenSpec：propose → apply → archive（更轻、按「一次变更」）

已经有书面 spec，只要落地任务清单
    → Superpowers writing-plans / executing-plans
    → 或 Spec Kit /speckit-tasks + /speckit-implement

写代码、修 bug、说「做完了」
    → Superpowers：TDD、systematic-debugging、verification-before-completion
```

**本仓库建议默认路径**

1. 产品规则、接口、非目标 → 改 `AGENTS.md`（必要时先 brainstorm）。
2. **第一条功能及之后的主流程** → Spec Kit（已接 constitution / git / gates / critique）。
3. 想快速开一个独立小变更、或和 Spec Kit 功能并行的局部改动 → OpenSpec。
4. 编码纪律（TDD、调试、验证、worktree、收尾）→ Superpowers，三套流程里都可以自动套上。

同一功能不要既跑完整 Spec Kit 又跑完整 OpenSpec。选定一套写完再归档。

---

## Superpowers

**是什么**：开发协作技能库（不是规格仓库）。Agent 在对应场景会自动用，也可用 `/` 调用技能名。

**产物位置**（若走 writing-plans）：`docs/superpowers/plans/YYYY-MM-DD-<feature>.md`。设计稿默认在 `docs/superpowers/specs/`。

### 常用技能

| 时机 | 技能 | 做什么 |
|---|---|---|
| 新功能、改行为、还没想清楚 | `brainstorming` | 先对齐意图，再写设计，**批准前不写产品代码** |
| 有 spec、要多步落地 | `writing-plans` | 写成可执行计划 |
| 按已有计划做 | `executing-plans` | 分检查点执行 |
| 当前会话拆独立任务 | `subagent-driven-development` | 子代理并行 |
| 多项互不依赖 | `dispatching-parallel-agents` | 并行调研/修改 |
| 需要隔离工作区 | `using-git-worktrees` | 独立 worktree |
| 写功能 / 修 bug | `test-driven-development` | 先测试再实现 |
| 测试失败、异常行为 | `systematic-debugging` | 先根因再改 |
| 声称完成之前 | `verification-before-completion` | 先跑验证看输出 |
| 合并前 | `requesting-code-review` | 对照需求审查 |
| 收到评审意见 | `receiving-code-review` | 核实后再改 |
| 实现完成、测试通过 | `finishing-a-development-branch` | 合并 / PR / 清理 |

会话开始会跑 Superpowers 的 `sessionStart` 钩子，无需手动开。

### 什么时候用 Superpowers

- **总是作为「怎么干活」**：无论 Spec Kit 还是 OpenSpec，编码阶段都应 TDD + 完成前验证。
- **单独用**：改一句文案、修一个明确小 bug、调研代码——不必开 Spec Kit / OpenSpec。
- **不要单独用它代替规格**：它不维护 `specs/` 能力基线，也不替代 `AGENTS.md`。

---

## Spec Kit（本仓库主 SDD 周期）

**是什么**：GitHub Spec Kit。规格、计划、任务、实现有门禁。配置在 `.specify/`，功能文档在 `specs/`（按功能编号）。

**完整周期**（`.specify/workflows/speckit/workflow.yml`）：

```
/speckit-specify  → 审 spec
/speckit-plan     → 审 plan
/speckit-tasks
/speckit-implement
```

也可以在对话里描述需求，让 Agent 按 workflow 跑，每步 approve / reject。

### 本仓库已接的扩展（会自动挂钩）

| 阶段 | 命令 | 说明 |
|---|---|---|
| 宪章前 | `/speckit-git-initialize` | 已是 git 仓库则跳过 |
| 宪章前（可选） | `/speckit-gates-constitution` | 引导式宪章访谈 |
| 写 spec 前 | `/speckit-git-feature` | **强制**开功能分支，不要在 `main` 上 specify |
| 写 spec 后（可选） | `/speckit-git-commit` | 提交 spec |
| implement 前 | `/speckit-api-evolve-gate` | 未声明的破坏性 API 变更会拦住 |
| plan 后（可选） | `/speckit-critique-run` | 产品/工程双镜头批 spec 和 plan |
| 实现后（可选） | `/speckit-gates-verify`、changelog | 质量门与变更说明 |

其它常用：

| 命令 | 何时 |
|---|---|
| `/speckit-constitution` | 项目原则、TDD、安全边界；当前 `.specify/memory/constitution.md` 仍是模板，**正式功能前应补完** |
| `/speckit-doctor` | 体检目录、脚本、扩展、git |
| `/speckit-clarify` | spec 里含糊处最多 5 个高优先级问题 |
| `/speckit-analyze` | spec / plan / tasks 交叉一致性 |
| `/speckit-checklist` | 按当前功能生成检查清单 |
| `/speckit-bug-assess` / `bug-fix` / `bug-test` | 以 bug 为入口，而不是新功能 |

### 什么时候用 Spec Kit

- 新能力会进主线、需要可审查的 spec → plan → 任务（例如：APP 登录、对话一轮、商品详情、转链、订单列表、Admin SSO）。
- 要和宪章、功能分支、API 演进门禁绑在一起。
- 需要 `specs/<feature>/` 作为该功能的交付物。

### 什么时候不要用 Spec Kit

- 只改文档、改锁定版本表、改 `AGENTS.md` 措辞。
- 纯重构且对外行为不变（可用 OpenSpec 小变更或直接 Superpowers + 测试）。
- 还在空转想法、没有「这一条功能」——先 brainstorm / explore。

---

## OpenSpec（轻量「一次变更」）

**是什么**：按 **change** 工作。本仓库 `openspec/config.yaml` 的 schema 是 `spec-driven`。

一次 propose 会生成：

- `proposal.md`：做什么、为什么
- `specs/<capability>/spec.md`：**相对主规格的 delta**，不是整本重写
- `design.md`：怎么做
- `tasks.md`：实现步骤

目录在 `openspec/`。`/opsx-apply` **只实现、不在同一轮里继续改规划**。

### 命令（Cursor 里以 `/opsx-*` 为准）

| 命令 | 何时 |
|---|---|
| `/opsx-explore` | 只思考、调查、澄清。**不写产品代码**。讨论清楚后再 `/opsx-propose` |
| `/opsx-propose` | 一句话需求 → 完整变更产物后停止，等你再说「开始实现」 |
| `/opsx-apply` | 按该 change 的 `tasks.md` 实现 |
| `/opsx-update` | 实现中途要改范围/设计，更新已有 change |
| `/opsx-sync` | 把已接受的 delta 合进主 specs |
| `/opsx-archive` | 实现完成后归档 change |

CLI 等价：`openspec`（explore / propose / apply 等）。项目未初始化时不要让命令副作用创建 `openspec/`；本仓库已经有 `openspec/`。

### 什么时候用 OpenSpec

- 变更边界清晰、想要「提案 → 实现 → 归档」短闭环。
- 和 Spec Kit 功能**并行**的局部能力（例如只加 Langfuse 字段、只加一张表），避免塞进正在写的大 spec。
- 先 explore 再决定要不要立项。

### 什么时候不要用 OpenSpec

- 项目级宪章、全仓库目录与双 jar 约定 → `AGENTS.md` + Spec Kit constitution。
- 已经在跑该功能的 Spec Kit 周期 → 在同一条功能上不要再 propose 一套重复 delta。

---

## 对照：三种工具 vs 本项目阶段

| 你现在要做的事 | 用 | 不要 |
|---|---|---|
| 补充/修正原始需求 | 改 [`product-input.md`](./product-input.md)，重要结论同步 `AGENTS.md` | 直接开写代码 |
| 定技术栈、API、表结构、非目标 | `AGENTS.md` | 只写在聊天记录里 |
| 写项目宪章 | `/speckit-constitution`（可选先 `/speckit-gates-constitution`） | 跳过宪章直接 implement |
| 「要不要做语音、客服做到哪」 | Superpowers brainstorming 或 `/opsx-explore` | `/speckit-implement` |
| 做「APP 登录」整条功能 | `/speckit-git-feature` → specify → plan → tasks → implement | 在 `main` 上直接写 Android/iOS |
| 做一个小 delta（例如只加记忆 TTL） | `/opsx-propose` → apply → archive | 再开一整条 Spec Kit 功能编号 |
| 编码 | Superpowers TDD | 无测试直接堆实现 |
| 对不上预期 | Superpowers systematic-debugging | 猜补丁 |
| 准备合并 | verification-before-completion → finishing-a-development-branch | 未跑通就宣称完成 |

---

## 推荐的日常口令

```text
# 主功能（Spec Kit）
/speckit-constitution
/speckit-specify 用户可以用微信和手机号登录 APP
# 审完 spec.md 后
/speckit-plan
/speckit-tasks
/speckit-implement

# 轻量变更（OpenSpec）
/opsx-explore
/opsx-propose add-order-sync-cursor
/opsx-apply
/opsx-archive

# 纪律（随时）
先写失败测试再改 Java 适配器
```

技能与命令都可以在输入 `/` 时搜到。Agent 也会在匹配场景自动选用 Superpowers，不必每次手打。
