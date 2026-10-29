# omino-shop-ai Constitution

## Core Principles

### I. 一套 Java，两个产出物

公共逻辑只放 `shop-api-common`。APP 与 Admin 不得互相 import。
对外前缀只有 `/api/app` 与 `/api/admin`。`/internal/v1` 只挂在 app 进程，
不是第三产出物。两个产出物不得监听同一端口。

理由：一套代码两套入口。再拆进程或互相引用会把契约和部署边界打穿。

### II. mall-guide 只读

允许阅读其搜品、详情、转链、订单游标流程，然后在本仓库重写适配层。
禁止复制其源码、Mapper、表名、包名，禁止把它当依赖 jar 或 HTTP 网关上线。

理由：参考仓库只提供流程理解。复制会把未授权结构和错误边界带进本仓库。

### III. 管理后台身份只走 omino-sso

管理后台登录必须是 OIDC Authorization Code + PKCE（仅 S256）。
Java 作 BFF：浏览器只打 Admin API，不得持有 access_token。
禁止自建管理员账号密码表。APP 登录与 SSO 隔离，APP 不接 omino-sso。
在 feat 2 的 SSO 完成前，同样禁止用本地密码表顶上。

理由：管理员身份只有一个来源。自建密码表会在 SSO 落地后留下第二套账号。

### IV. 密钥与转链只在 Java，对话链路固定

联盟密钥、PID、relationId 只存在于 Java 环境变量。
模型密钥加密存储，日志与响应不得出现明文密钥、PID 或堆栈、SQL。
对话路径固定为 Android / iOS / H5 → Java SSE → Agent。
开 run 后 Worker 调 Agent，事件进 Redis，GET SSE 泵出。
断开 SSE 不得打断生成。停止生成走 Redis cancel，不得用图中断充当停止。
Tool 只调 app 的 `/internal/v1`。Agent 不对公网、不对 APP 直连。
点击详情后再转链。邀请海报由客户端贴二维码，Java 只存模板与矩形。
积分扣费只在 Java。积分与返佣钱包分户。禁止积分提现，禁止积分转回钱包。

理由：密钥和钱出了 Java 就无法审计。生成与连接解耦后，刷新不得变成取消。

### V. 主库唯一，先改权威文档再实现

主库只有 PostgreSQL + pgvector。禁止 MySQL。商品不落全量库。
意图用 `docs/conversation.md` 的点分枚举。导购与客服同一张图分流。
与 `AGENTS.md` 或其 §4 索引文档冲突的实现视为缺陷。
改行为必须先改对应权威文档，再改测试，再改代码。
同类字段不得在两份文档里各写一套。

理由：契约漂移比缺功能更贵。文档是字段与事件名的唯一出处。

### VI. 先检索再实现

不熟悉的能力（SSE、OIDC、向量检索、OSS 直传、验证码、联盟 SDK 等）
不得立刻从零手写协议、编解码或会话机。
先检索 GitHub 上近 12 个月仍活跃、许可证为 MIT / Apache-2.0 / BSD、
star 与生态匹配的同类项目，优先引入或对照其用法适配。
拒绝 GPL / AGPL。引入依赖必须先改 `AGENTS.md` §2，锁定精确版本，
写明用哪部分、不用哪部分，且不得抬升已锁定版本。
本条不授权复制 mall-guide。

理由：手写协议是缺陷高发区。版本表是依赖的唯一登记处。

## 非目标与硬边界

以下能力在规范明确改口前不得实现，也不得用空接口、空表或 TODO 充当完成。

1. 不做自营购物车、商品支付、第三方售后。商品下单仍在电商 APP。
   钱包与提现只处理本平台返佣。例外仅限用返佣余额购买对话积分。
2. 不做 LangSmith、多 Agent 进程、开放插件市场、对话直连 MCP。
   知识库 MCP 只允许替换 `search_kb`。
3. 不做管理后台的微信 / Apple 登录。
4. 不做人工实时坐席。客服不得编造物流或政策。工单只建单，运营在 Admin 处理。
   Mem0 / Letta 不得作为主记忆库。
5. 不做 Flutter、React Native、Kotlin Multiplatform。
   消费者端只有 Android、iOS 原生与 `apps/h5`。上帝视角默认不上生产。
6. 不做美团。平台只有 `taobao`、`jd`、`pdd`、`vip`。

另外必须遵守、但不在本文件展开字段的边界：

- 目录与分层以 `AGENTS.md` §3 为准：web 不写 SQL、不调联盟 SDK；
  service 不依赖 Servlet 类型；mapper 不做业务分支。
- HTTP JSON 用 `AGENTS.md` §5 信封。业务码以该表为准，禁止把密钥写入 `message`。
- 技术栈版本以 `AGENTS.md` §2 为准。宪章不另锁一套版本号。
- 密钥类环境变量禁止写入 Git，禁止明文进 Nacos。

## 交付流程

1. 每次用户请求先在 `docs/product-input.md` 主表末尾追加一行，再办事。
   禁止改写或删除已有行。
2. 一次只做一个 Spec Kit 功能。阶段划分以 `docs/speckit-phases.md` 为准。
   禁止把整份 `AGENTS.md` 丢进 `/speckit-specify`。同一功能不要再跑 OpenSpec。
3. 顺序固定：功能分支 → specify → 人审 spec → plan → 人审 plan → tasks → implement。
   依赖未完成不得开下一个 feat。
4. 测不过不算完成。Java 按 common 的 dto / entity / mapper 测试、
   再 service 测试、再 web 的顺序写。Agent 节点必须可单独测试。
5. 未排到当前 feat 的能力留在该 feat，不提前建表、不预留空接口。

## Governance

本宪章是 `AGENTS.md` §7 与附录 B 在 Spec Kit 中的可检查副本。
产品字段、路径、意图、表结构和版本号仍以 `AGENTS.md` 及其索引文档为准。
二者冲突时改宪章去对齐权威文档，不得反过来改权威文档来迁就宪章。

修订程序：先改 `AGENTS.md`（或确认权威文档已包含该规则），再改本文件。
版本规则：不兼容地删除或重定义原则升 MAJOR；新增原则或实质加严升 MINOR；
措辞澄清升 PATCH。修订须写明理由。本文件顶部的 Sync Impact Report
只供当次审阅，提交前删除。

合规：`/speckit-specify`、`/speckit-plan`、`/speckit-tasks`、`/speckit-implement`
的产出不得违反上述原则。评审对照本文件与 `AGENTS.md` §7。
复杂化必须在对应权威文档里写明理由，否则视为缺陷。

**Version**: 1.0.0 | **Ratified**: 2026-10-29 | **Last Amended**: 2026-10-29
