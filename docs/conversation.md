# 对话设计（意图、图、记忆、SSE）

> 本文件是对话契约的权威正文。技术栈/约束见 [`AGENTS.md`](../AGENTS.md)；表结构见 [`data-model.md`](./data-model.md)；App 开 run 以外的 HTTP 见 [`api-app.md`](./api-app.md)；Tool 见 [`api-internal.md`](./api-internal.md)。运行时节点说明见 [`agent.md`](./agent.md)，冲突以本文为准。

信封、错误码见 `AGENTS.md` §5。

### 4.0 意图、图、记忆（对话总则）

工业界惯例（本项目锁定）：**分层意图 + 结构化槽位 + 置信度门控 + 单轮单主意图 + 工具接地 + HITL 中断恢复**。LLM 只做路由/抽槽/指代/话术；搜品、查单、授权、钱包、积分扣费、工单全部走 Java 工具。

**一张 StateGraph**（同进程 subgraph，禁止独立客服 Agent）：

`ingest → intent_router → (shop | account | order | earn | support | chitchat) → (HITL interrupt?) → reply_sse`

一句多意图时：本轮只跑 `primaryIntent`，其余写入工作记忆 `pendingIntents[]`，下一轮继续。`confidence < 0.6` 发 `clarify`，不调搜品工具。

**一级意图（`intent` 字段必须是下表点分名，禁止再用 `shopping` 这种粗类）**


| intent          | 典型用户话                 | 槽位（未填则 clarify）                                         | 允许的 Tool                                | SSE 主产物                           |
| --------------- | --------------------- | ------------------------------------------------------- | --------------------------------------- | --------------------------------- |
| `shop.search`   | 「我要买纸巾」               | `category`/`scene`/`keywords[]`/`budget*`/`platforms[]` | `search_goods`                          | ≤6 条 `product_cards`；**>6** 则 `collection_card`（禁止再逐条卡） |
| `shop.gift`     | 「七夕送女朋友买什么」           | `occasion`/`recipient`/`budget*`/`keywords[]`           | 先澄清再 `search_goods`（多平台）                | 同上                                |
| `shop.scene`    | 「孩子快开学要准备什么」          | `scene`/`grade?`/`budget*`/`itemList[]`                 | 先澄清范围，再按清单逐类 `search_goods`             | 同上                                |
| `shop.link`     | 粘贴淘宝/京东商品 URL         | `sourceUrl`                                             | `parse_goods_link`                      | **单张** `product_card`             |
| `shop.image`    | 发商品图                  | `imageObjectKey`                                        | `parse_goods_image` → 可选 `search_goods` | 单卡、≤6 列表，或合集卡                     |
| `shop.compare`  | 「这两款哪个好」              | `referents[]`（卡片 id）                                    | `goods_detail`（缓存）                      | 文本，不重新海搜                          |
| `me.profile`    | 「我是谁」「你对我了解多少」「何时注册」  | —                                                       | `get_me` + 读语义记忆                        | 文本                                |
| `me.auth_list`  | 「我授权了哪些平台」            | —                                                       | `list_authorizations`                   | 文本                                |
| `me.stats`      | 「这月几单、花多少、多少返佣」       | `period` 默认本月                                           | `get_order_stats` + `get_wallet`        | 文本                                |
| `me.credits`    | 「我还有多少积分」             | —                                                       | `get_credits`                           | 文本；不足引导购套餐，不经 Agent 调支付           |
| `order.query`   | 「前几天京东那单发货了吗」「返佣何时到账」 | `platform?`/`orderId?`/`timeHint?`                      | `list_orders` / `get_order`             | 文本；无单则说不知道                        |
| `auth.grant`    | 「淘宝怎么授权」              | `platform`                                              | **HITL** `start_authorization`          | `auth_card`；用户完成授权后 Java 推/下轮提示成功 |
| `auth.revoke`   | 「取消淘宝授权」              | `platform`                                              | **HITL** 确认卡 → `revoke_authorization`   | `confirm_card`                    |
| `earn.invite`   | 「这月邀请多少人、有效用户、下级贡献」   | `period`                                                | `get_affiliate_stats`                   | 文本                                |
| `kb.faq`        | 产品规则咨询                | `question`                                              | `search_kb`；未命中则引导 `ticket.create`      | 文本或 `clarify` 去建单                 |
| `ticket.create` | 问题/建议反馈               | `category`/`content`/`orderId?`                         | `create_ticket`                         | `ticket_created`                  |
| `chitchat`      | 闲聊                    | —                                                       | 无业务 Tool                                | 短文本                               |
| `handoff`       | 资金纠纷/辱骂/无法判定          | —                                                       | `create_ticket`（加急）                     | 工单号                               |


指代：优先绑 **本轮已下发的商品卡 `goodsId`**，其次工作记忆最近 `item`/`orderId`。客服/查账类 **默认不写长期偏好**，除非用户明确说「记住以后…」。

**记忆三分法（与存储对应见 [`data-model.md`](./data-model.md) §6.6）**


| 层        | 业界对应          | 本项目存储                                                    | 用途                                   |
| -------- | ------------- | -------------------------------------------------------- | ------------------------------------ |
| Working  | 会话状态 / thread | LangGraph `PostgresSaver`（thread_id = conversationId）    | 槽位、pending 意图、本轮卡片 id、HITL interrupt |
| Episodic | 事件记忆          | `user_event`                                             | 浏览/点击/下单/授权，供「上次那种」、过滤已买             |
| Semantic | 长期画像          | `app_user_preference` 结构化列 + `memory_semantic`（pgvector） | 问卷/高置信「记住」；开放句用向量检索                  |


对话抽取的偏好：`confidence < 0.8` 只进候选，确认或问卷才 upsert 正式画像。

### 4.1 对话：生成与 SSE 解耦（Redis 事件流）

**锁定**：跑图 **不得**绑在一条 HTTP/SSE 连接上。刷新、闪断、换副本，只影响「读事件」，不影响 Agent 是否继续跑。

```
POST /api/app/v1/chat/turns     → 校验、落用户消息、启动 run（立刻返回 runId）
后台 Worker（任意 app 副本）     → 调 Agent SSE，每条事件 XADD 到 Redis Stream
GET  /api/app/v1/chat/runs/{runId}/events  → SSE；泵从 Redis 读，间隔 ≤100ms
```


| 项    | 规定                                                                                              |
| ---- | ----------------------------------------------------------------------------------------------- |
| 事件存储 | Redis Stream 键 `chat:run:{runId}`，字段 `id`（单调，作 SSE `id:`）、`event`、`data`                        |
| 写入方  | **仅 Java Worker**。Agent 仍对自己的 HTTP 调用方流式输出；Worker 消费后写入 Redis，**禁止**把 Agent 流直接对 APP            |
| 读出方  | SSE 泵：`XRANGE`/`XREAD` 从 `Last-Event-ID` 之后取；无新数据时等待，循环间隔 **≤100ms**（可用 `XREAD BLOCK 100`）      |
| 断线续传 | 客户端重连带 `Last-Event-ID`（或 query `after=`）。已写入 Redis 的事件必须能重放，直到 TTL                              |
| 断开生成 | SSE `onCompletion` **禁止**取消 Worker / 禁止中断对 Agent 的调用                                            |
| 多副本  | 无粘滞会话。任一 `shop-api-app` 可启动 run、可泵事件，只认同一 Redis                                                 |
| 幂等   | 同一 `conversationId+clientMsgId` 已有 run：POST 返回同一 `runId`，不新开图                                   |
| TTL  | `done`/`error`/`cancelled`/`credit_exhausted` 后键再保留 **30 分钟**；超时未结束的 run 由 Worker 写 `error` 并结束 |
| 取消   | 仅 `POST /api/app/v1/chat/runs/{runId}/cancel`（见 §4.1.2）。刷新 ≠ 取消                                 |
| 积分   | 开 run 与流中由 Java 扣积分（§4.1.3）。余额不足终态 `credit_exhausted` + `credit_card`，**不是** HITL `interrupt`   |


**POST**  `/api/app/v1/chat/turns` 响应 `data`（JSON，不是 SSE）：

```json
{
  "conversationId": "01JABCDEFGHJKMNPQRSTVWXYZ",
  "runId": "01JRUNEXAMPLE0000000000000",
  "clientMsgId": "cmsg_7f3c2a1b"
}
```

然后客户端再 **GET** `/api/app/v1/chat/runs/{runId}/events`（Bearer，SSE）。无 SSE 能力时可用同一 GET 改轮询 JSON 增量，事件语义相同。

**GET SSE 事件名**仍用下表。每条必须带 SSE `id:`（Redis 消息 ID），便于续传。

---

#### 4.1.1 请求体（POST 开 run）

Android/iOS/H5 → Java（JWT）。Java 写入用户消息后 **异步** 调 Agent（[`api-internal.md`](./api-internal.md) invoke），不在 POST 线程里等整图结束。

**Request**

```json
{
  "conversationId": "01JABCDEFGHJKMNPQRSTVWXYZ",
  "clientMsgId": "cmsg_7f3c2a1b",
  "inputType": "text",
  "text": "我要买纸巾",
  "sourceUrl": null,
  "imageObjectKey": null,
  "hitl": null,
  "locale": "zh-CN",
  "device": {
    "os": "ios",
    "osVersion": "17.5",
    "clientType": "ios",
    "channel": "appstore",
    "versionCode": 12,
    "versionName": "0.1.0",
    "installedPlatforms": ["jd", "taobao", "pdd"]
  }
}
```


| 字段             | 类型     | 约束                                         |
| -------------- | ------ | ------------------------------------------ |
| conversationId | string | 可空；空则创建                                    |
| clientMsgId    | string | 必填，≤64，幂等键                                 |
| inputType      | string | `text`                                     |
| text           | string | `text`/`voice_transcript` 时必填，trim 1–2000  |
| sourceUrl      | string | `goods_url` 时必填，http(s) 商品链接               |
| imageObjectKey | string | `image` 时必填，先走上传接口得到对象键                    |
| hitl           | object | HITL 恢复：`interruptId` + `action`（`confirm` |
| device.*       |        | `clientType`/`channel`/`versionCode` 须与请求头一致；`installedPlatforms` 仅 `taobao`/`jd`/`pdd`/`vip` |


Java 在 **启动 Worker 前** 写入用户消息、生成 `runId`、注入 `userId`、已装平台、授权平台列表。Worker 调 Agent 时带这些字段。SSE 泵与 Worker **无共享堆对象**，只共享 Redis。

**SSE 事件（`event:` 名锁定）**


| event              | data 要点                                                                                               |
| ------------------ | ----------------------------------------------------------------------------------------------------- |
| `intent`           | `{ "intent", "confidence", "slots" }`                                                                 |
| `text_delta`       | `{ "text" }` 增量                                                                                       |
| `clarify`          | `{ "question", "missingSlots": [] }`                                                                  |
| `product_card`     | 一张商品卡（`shop.link` 仅此一张）                                                                               |
| `product_cards`    | `{ "goods": [ /* GoodsCard */ ] }` 至多 6 张                                                             |
| `collection_card`  | `{ "collectionId", "title", "total", "covers": [ /* 最多 4 张 GoodsCard 瘦字段 */ ] }` 点进合集页           |
| `auth_card`        | `{ "platform", "authUrl", "interruptId" }` HITL 授权                                                    |
| `confirm_card`     | `{ "action", "platform", "interruptId", "prompt" }` HITL 确认取消授权等                                      |
| `ticket_created`   | `{ "ticketId", "status" }`                                                                            |
| `usage`            | `{ "promptTokens", "completionTokens", "totalTokens", "credits", "model" }` 本 run 累计；Agent 上报，Java 扣费 |
| `credit_card`      | `{ "reason", "balance", "packages": [ /* CreditPackage 对外字段 */ ] }` 购积分卡                              |
| `error`            | `{ "code", "message" }` 不泄露密钥                                                                         |
| `done`             | `{ "conversationId", "serverMsgId", "intent" }`                                                       |
| `cancelled`        | `{ "runId" }` 用户终止；与 `done`/`error`/`credit_exhausted` 同为 **终态**                                      |
| `credit_exhausted` | `{ "runId", "reason" }` `INSUFFICIENT_START` | `EXHAUSTED_MID`；泵停止                                    |


`GoodsCard` 字段与详情卡片的列表态一致：`goodsId, platform, platformGoodsId, title, imageUrl, price, couponPrice, salesCount, reviewCount, goodsUrl, shopName, reason?`。`salesCount` / `reviewCount` 为整数或 `null`；`goodsUrl` 为平台商品页，可空，不是购买链接。

HITL：Worker 把 `auth_card`/`confirm_card` 写入 Redis 后 **结束本 run**（图已 interrupt 并 checkpoint）。用户授权或确认后 **再 POST 一轮**（带 `hitl`），新 `runId`，从 checkpoint resume。刷新不会丢 interrupt 状态。联盟回调成功亦可只插 `system` 消息，无需新 run。

图片：先 `POST /api/app/v1/files/image`（multipart，鉴权同 APP）拿到 `imageObjectKey`，再作为 `inputType=image` 进入本接口。

#### 4.1.2 `POST /api/app/v1/chat/runs/{runId}/cancel` — 用户点终止

刷新、切后台、SSE 断开 **不得**走本接口。只有 UI「停止生成」才调用。

**不能**指望「取消内存里的 StateGraph 对象」：Worker 可能在另一副本。取消信号必须进 Redis，任意副本可写、跑图的那台可读。


| 步骤  | 行为                                                                                                                      |
| --- | ----------------------------------------------------------------------------------------------------------------------- |
| 1   | 校验 JWT，且 `runId` 属于当前用户。已终态（`done`/`error`/`cancelled`/`credit_exhausted`）→ `200` 幂等，不报错                                |
| 2   | `SET chat:run:{runId}:cancel 1`（无 TTL 或与 Stream 同 30 分钟）                                                                |
| 3   | **本机若没有 Worker 也没关系**。跑图的 Worker 在读 Agent 流的循环里 **每条事件后**（以及等待超时醒来时）检查该键                                                |
| 4   | 一旦看到 cancel：立刻 **abort** 对 Agent 的 HTTP（掐断 LLM/Tool），不要等当前节点「正常跑完」                                                      |
| 5   | `XADD` 事件 `cancelled`，Stream 进入终态并套 TTL。已写入的 `text_delta` **保留**，续传仍能看到停止前的字                                            |
| 6   | **不**把助手半成品当正式 `conversation_message` 落库；或落一条 `role=assistant` 且 `payload.status=cancelled`（实现选一种，推荐落库便于 UI 刷新后还能看到半段字） |


Agent（FastAPI）侧：Java 一 abort，invoke 的请求断开。生成器必须把 `asyncio.Task.cancel()` 打到正在跑的 `graph.astream(...)` 上，使 LangGraph 抛出 `CancelledError` 并结束，**不要**再往已断开的响应里写。httpx Tool 随 task 取消而取消。

Checkpointer：取消发生在某节点中途时，**不写半截 node 状态**；线程上仍是上一轮成功 checkpoint。用户下一句是新 `runId`、同一 `conversationId`，从完整 checkpoint 继续。不要 `update_state` 强行 `END` 污染槽位。

禁止：用 LangGraph `interrupt()` 模拟停止（那是 HITL）；禁止只 cancel 本 JVM 的 `SseEmitter`。

#### 4.1.3 积分不足：中途停 + 购积分卡

积分账户与返佣 `wallet` **分户**。扣费、入账、支付回调 **只在 Java**。Agent 只通过 SSE 事件 `usage` 上报 token，**禁止** Tool 改积分、禁止持支付密钥。

**比例**（Nacos `credit.tokens-per-credit`，默认 `1000`，热更新）：`credits = round(billed_tokens / tokens_per_credit, 2)`，`RoundingMode.HALF_UP`。`billed_tokens = prompt_tokens + completion_tokens`。若 `billed_tokens > 0` 且结果 `< 0.01`，按 **0.01** 起扣。未调用 LLM 的 run 不扣。Java Tool HTTP **不计** token。不落 `credit_rate` 表。

**余额禁止为负。** `credit.enforce=false` 仅 `local` 且 Nacos/yml 允许（Demo）；`test`/`prod` 必须 `true`。开跑阈值：`available < 0.01` 视为不足。


| 时机         | 行为                                                                                                                                                                                                                                                 |
| ---------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 开 Worker 前 | `available < 0.01` 且强制扣费：不调 Agent；`XADD` `credit_card`（`reason=INSUFFICIENT_START`）+ `credit_exhausted`；POST turns 仍返回 `runId`（客户端照常接 SSE）                                                                                                         |
| 流中         | Agent 每完成一次 LLM 调用（及流式过程中的 usage 心跳）发 `usage`。Java 按增量扣 `credit_account` 并写 `llm_usage` + `credit_ledger.type=consume`。下一跳将使余额不足：与 cancel 相同 **abort Agent HTTP**，再 `credit_card`（`reason=EXHAUSTED_MID`）+ `credit_exhausted`。已输出的 `text_delta` 保留 |
| `done`     | 用厂商最终 usage **多退少补**：多扣则 `credit_ledger.type=refund`；少扣且余额不够则只扣到 0，不透支                                                                                                                                                                             |


`credit_card.data.packages` 为当前 `active` 套餐对外字段（`id,name,credits,price,sort`），**不含**成本。客户端拉起支付走 [`api-app.md`](./api-app.md) 积分套餐，**不**把支付当 LangGraph `interrupt`。购入成功后用户再 `POST turns` 新 `runId`。

Checkpointer 规则与取消相同：不写半截 node。

