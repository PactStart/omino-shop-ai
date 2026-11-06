# Implementation Plan: 对话助手

**Branch**: `005-agent` | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/005-agent/spec.md`

## Summary

已登录用户的一轮发言不再以本地固定问候结束。购物服务把这一轮交给本机助手；助手按 [`docs/agent.md`](../../docs/agent.md) 的一张图识别意图，只通过已有内网工具读取商品和账户，再按 [`docs/conversation.md`](../../docs/conversation.md) 的事件名流式返回。授权才暂停。下一句从暂停点恢复，不再跑意图路由。用量按本轮累计只扣增加量。这句话没说的预算和平台，用已经明确记住的那份。停止仍由购物服务掐断这次调用。

选择见 [research.md](research.md)。

## Technical Context

**Language/Version**: 助手 CPython `3.12.8`。购物服务仍是 Java 21（Temurin `21.0.8`）。

**Primary Dependencies**: 已锁定并写入 `pyproject.toml` 的 `langgraph==1.2.20`、`langchain-core==1.7.6`、`langchain-openai==1.7.4`、`langfuse==2.57.0`、`psycopg[binary]==3.2.4`。工作记忆使用与 `langgraph` `1.2.20` 配套的 `langgraph-checkpoint-postgres==3.2.0`（MIT）。该精确版本在加入依赖前写入 `AGENTS.md` §2.2。`httpx==0.28.1` 是运行依赖。不把 LangSmith 当作观测，不引入第二套模型 SDK，不使用 checkpoint 包里的 Store。

**Storage**: 不新增业务表，不写 Flyway。工作记忆用官方 `PostgresSaver.setup()` 在现有 PostgreSQL 16 上建 checkpoint 表，`thread_id` 为会话编号。长期偏好仍用已有 `memory_semantic`。浏览、点击、下单、授权事件仍只由购物服务写入 `user_event`。

**Testing**: 助手用 pytest `8.3.4`。路由纯函数覆盖每条意图和低于 0.6 的把握。工具用 `httpx` 自带的模拟传输，不另加测试库，不打真联盟、不打真模型。购物服务用现有 JUnit 5 覆盖请求体、本地回退和取消掐断。端到端步骤见 [quickstart.md](quickstart.md)。

**Target Platform**: 助手只听 `127.0.0.1:8190`。消费者仍只打 APP `:8181`。

**Project Type**: 在现有 `services/agent` 里接上图，并让 `shop-api-app` 的工人把已有对话字段送进去。不新增 Java 产出物，不改 Admin。

**Performance Goals**: 沿用对话超时 `chat.run-timeout-seconds`（默认 120 秒）。单次工具调用超时 5 秒。不另定吞吐指标。

**Constraints**: 工具只打 APP 的 `/internal/v1`，白名单见 [`docs/api-internal.md`](../../docs/api-internal.md)。不转链，不改积分和钱包，不持有联盟密钥。提示词只使用当次注入的八条，模型同一轮只有一个。停止不用授权式暂停。生产必须开追踪。日志、追踪、异常输出和用户可见文字不含模型密钥、联盟密钥和 PID。异常日志只留状态码和追踪号，不打印调用正文；追踪送出前去掉 `apiKey`。开一轮按输入类型校验：文本和语音转写必须有正文，商品链接必须有链接，商品图必须有图片键。`hitl` 非空时允许没有正文，确认动作以 `hitl` 为准。搜品的预算和平台以这句话为准，为空时用未过期的明确记忆。

**Scale/Scope**: 一张图覆盖 [`docs/conversation.md`](../../docs/conversation.md) 的意图表。本地在助手进程没起来时仍回固定问候。测试和生产没有提示词或可抽模型时不调用助手。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 计划前 | 设计后 |
| --- | --- | --- |
| I. 一套 Java，两个产出物 | 通过。只改 app 进程里已有的工人和内网工具。Admin 不参与 | 通过。没有第三个进程，两边不互相 import |
| II. mall-guide 只读 | 通过。不读参考仓库来做图 | 通过。工具只调用本仓库已有内网路径 |
| III. 管理后台只走 omino-sso | 通过。本功能不碰管理员登录 | 通过。无密码表 |
| IV. 密钥与转链只在 Java | 通过。模型密钥只活在当次请求。停止走现有取消标记并掐断 HTTP。授权才用图的暂停 | 通过。契约里没有转链和改余额。追踪不记录密钥 |
| V. 主库唯一，先改文档再实现 | 通过。不新增业务表。记忆写入先补 [`docs/api-internal.md`](../../docs/api-internal.md) | 通过。data-model 只描述既有表和官方 checkpoint 表的用途 |
| VI. 先检索再实现 | 通过。图、检查点和追踪都用已锁定或本次核过的库，不手写会话机 | 通过。research.md 写明不用 3.x checkpoint，也不用其 Store |
| VII–XI. 实体、外键、枚举、OpenAPI、注释 | 通过。不新建实体、不建外键。新的内网映射补 `@Operation` 与 Javadoc | 通过。意图仍是契约里的点分名，不另造 Java 枚举表 |
| 非目标 | 通过。不做 LangSmith、第二套助手、MCP、Flutter、美团 | 通过。知识库仍走现有检索 |

无未解决的 NEEDS CLARIFICATION。

## Project Structure

### Documentation (this feature)

```text
specs/005-agent/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── invoke.md
│   └── tools.md
└── tasks.md              # 由 /speckit-tasks 生成，本命令不创建
```

[`docs/conversation.md`](../../docs/conversation.md) 与 [`docs/api-internal.md`](../../docs/api-internal.md) 已有的事件名和路径不在 `contracts/` 再抄一遍。

### Source Code (repository root)

```text
services/agent/
├── pyproject.toml
├── app/
│   ├── main.py                         # /health 与内网鉴权
│   ├── api/invoke.py                   # POST /internal/v1/graph/invoke
│   ├── graph/state.py
│   ├── graph/builder.py                # 一张 StateGraph
│   ├── graph/route.py                  # 无 I/O 的路由
│   ├── graph/nodes/                    # ingest、记忆、意图、指代、追问、闲聊、暂停、回复
│   ├── graph/subgraphs/                # shop、account、order、earn、support
│   ├── tools/java_client.py
│   └── observability/langfuse_setup.py
└── tests/

services/java/shop-api-app/
└── src/main/java/com/omino/shopai/app/
    ├── service/chat/                   # 请求体改由 Jackson 组装；本地可回退
    ├── service/credit/                 # 收到 usage 后按已有规则扣费
    └── web/internal/                   # 补上记忆检索和写入
```

**Structure Decision**: 目录按 `AGENTS.md` §3。助手不对消费者监听。购物服务继续负责开一轮、推事件、取消和扣积分。图内节点调用工具客户端，工具客户端不反向调用助手。

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
| --- | --- | --- |
| 无 |  |  |

本地在连不上助手时回固定问候，是同一条工人上的失败分支，不是第二套助手。

## Phase 0

研究结论在 [research.md](research.md)。没有留下待澄清项。

## Phase 1

- 数据：见 [data-model.md](data-model.md)。无业务迁移。
- 契约：见 [contracts/invoke.md](contracts/invoke.md) 与 [contracts/tools.md](contracts/tools.md)。记忆写入实现前先写入 [`docs/api-internal.md`](../../docs/api-internal.md)。授权恢复和累计扣费以 [research.md](research.md) 的对应两节为准。
- 验收步骤：见 [quickstart.md](quickstart.md)。

设计完成后已按上表重查宪章。原则 I 到 XI 通过。`langgraph-checkpoint-postgres` `3.2.0` 在写入 `pyproject.toml` 之前已经写进 `AGENTS.md` §2.2。
