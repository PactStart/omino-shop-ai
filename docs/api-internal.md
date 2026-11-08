# Internal API（Agent ↔ Java）

> 本文件是内网契约的权威正文。仅 `shop-api-app` 调 Agent；Agent Tool 只打本进程 `/internal/v1/**`。禁止 internal 再调 Agent。信封见 `AGENTS.md` §5。

### 4.4 Agent 内网 `POST /internal/v1/graph/invoke`（SSE）

仅 `shop-api-app` 可调用。`Accept: text/event-stream`，事件名与 [`conversation.md`](./conversation.md) 相同。

**Request**

```json
{
  "traceId": "01JTRACE",
  "userId": "u_01JUSER",
  "conversationId": "01JABCDEFGHJKMNPQRSTVWXYZ",
  "clientMsgId": "cmsg_7f3c2a1b",
  "inputType": "text",
  "text": "我要买纸巾",
  "sourceUrl": null,
  "imageObjectKey": null,
  "hitl": null,
  "installedPlatforms": ["jd", "taobao"],
  "authorizedPlatforms": ["jd"],
  "prompts": {
    "intent.router": "...",
    "clarify": "...",
    "reply.shop": "...",
    "reply.account": "...",
    "reply.order": "...",
    "reply.earn": "...",
    "reply.support": "...",
    "reply.chitchat": "..."
  },
  "llm": {
    "id": "01JMODEL",
    "baseUrl": "https://api.deepseek.com",
    "model": "deepseek-chat",
    "apiKey": "<当次注入，日志禁止记录>",
    "timeoutMs": 60000
  }
}
```

`prompts` 的 key 与 [`data-model.md`](./data-model.md) §6.29 一致。Java 从 `agent_prompt` 读出后注入；缺行或空白用内置默认正文补齐。默认正文包含角色、背景、上下文、目标、输出格式、约束和示例。已有非空正文保持原样。Agent 不读该表。请求里某一键仍是空白时，Agent 在调用模型前再补一次同一套默认正文。

`llm` 为本次 run 按权重命中的那一条（§6.30）。这一轮里每一次模型调用都用它。`apiKey` 只走这条内网请求，Worker 与 Agent 日志、Langfuse 都禁止落盘。`local` 且没有任何可分流模型时，`llm` 为 `null`，Agent 改用自身 `OPENAI_*` 环境变量。`test` / `prod` 不允许 `llm=null`。

记忆由 Agent 按 `conversationId` 读 Checkpointer；Java **不要**每次塞全量向量。语义记忆的读写走 `search_memory` 和 `write_memory`，助手不直连 `memory_semantic`。Agent 不持有模型密钥的长期副本，进程内只保留当次请求。

### 4.5 Java `/internal/v1/**`（仅 Agent Tool；第三产出物不存在）

挂在 `**shop-api-app` 同一进程**。鉴权：`X-Internal-Token` + `X-User-Id`（必须等于当前对话用户，禁止 Agent 指定他人）。下列路径 **不得** 再请求 Agent。


| Tool 名                      | 方法路径                                                     | 用途                                                                              |
| --------------------------- | -------------------------------------------------------- | ------------------------------------------------------------------------------- |
| `search_goods`              | `POST /internal/v1/goods/search`                         | body：`keywords[]`、`budgetMin/Max`、`platforms[]`、`scene?`。返回 `goods[]`；**条数 > 6** 时 Java 建合集并带 `collectionId`、`total`，`goods` 仅预览最多 4 张。出站前要有当前用户的 `relation_id`（拼多多用京东那条），没有则 `40301` |
| `parse_goods_link`          | `POST /internal/v1/goods/parse-link`                     | body：`sourceUrl`；解析平台+商品 ID，详情缓存后返回 **单张** 卡                                    |
| `parse_goods_image`         | `POST /internal/v1/goods/parse-image`                    | body：`imageObjectKey`；OCR/识别平台与标题，能定位则单卡，否则 keywords 再 `search_goods`           |
| `goods_detail`              | `POST /internal/v1/goods/detail`                         | 与 4.2 同构，走 Redis                                                                |
| `get_me`                    | `GET /internal/v1/me`                                    | 昵称、注册时间、偏好摘要（供「我是谁」「你了解我多少」）                                                    |
| `list_authorizations`       | `GET /internal/v1/me/authorizations`                     | 各平台授权状态                                                                         |
| `start_authorization`       | `POST /internal/v1/me/authorizations/start`              | body：`platform`；返回 `authUrl`（HITL 卡片用）                                          |
| `revoke_authorization`      | `POST /internal/v1/me/authorizations/revoke`             | body：`platform`；须已 HITL confirm                                                 |
| `list_orders` / `get_order` | `GET /internal/v1/orders`、`GET /internal/v1/orders/{id}` | 仅当前用户。含 `platformOrderId` `relationId` `orderStatus` `settleStatus` `userCommission` `payAmount`。`relationId` 可空。列表可带 `keyword`，匹配标题或平台订单号。无物流则 `logistics=null`。禁止返回 `mediaCommission` 与联盟原始 JSON，禁止编造 |
| `get_order_stats`           | `GET /internal/v1/orders/stats`                          | query：`from`/`to`；单量、实付合计                                                       |
| `get_wallet`                | `GET /internal/v1/wallet`                                | 可用/冻结/本月预估返佣                                                                    |
| `get_credits`               | `GET /internal/v1/credits`                               | 积分余额（字符串两位）与当前 `tokensPerCredit`；只读，禁止扣费                                        |
| `get_affiliate_stats`       | `GET /internal/v1/affiliate/stats`                       | 邀请人数、有效用户、下级贡献收益                                                                |
| `search_kb`                 | `POST /internal/v1/kb/search`                            | 规则问答；**未命中必须明确 miss**，不得让模型补政策。后期知识库 MCP 只替换本工具实现                               |
| `create_ticket`             | `POST /internal/v1/tickets`                              | body：`category`、`content`、`orderId?`、`priority`；同一事务写 `ticket_log.kind=created` |
| `search_memory`             | `POST /internal/v1/memory/search`                        | 记忆节点读取。body 可空或 `{ "limit": 20 }`，`limit` 缺省 20、最大 20。只返回当前用户且 `valid_to` 为空的行，含 `id` `kind` `text` `confidence` `source` `createdAt`，不含 `embedding` |
| `write_memory`              | `POST /internal/v1/memory`                               | 只由记忆节点调用，不放进模型可挑选的工具。body：`kind` `text` `confidence` `source`，可选 `supersedeId`。`source` 只接受 `explicit` 和 `inferred`，`survey` 拒绝。`confidence` 为 0 到 1。`inferred` 且低于 0.8 只插入候选，不覆盖。`supersedeId` 必须属于 `X-User-Id`，成功时给旧行写 `valid_to` 再插入。用户编号、主键、创建时间由服务端填写，`embedding` 留空。客服、查账、工单不得调用 |


**工具白名单**

- `shop.*`：search / parse-link / parse-image / goods_detail；**禁止** 转链、改钱包。
- `order.*` / `me.stats`：订单与统计只读。
- `auth.*`：仅 start/revoke。
- `kb.faq`：仅 `search_kb`，禁止 search_goods。
- `ticket.create`：仅 `create_ticket`。
- `me.credits`：仅 `get_credits`。**禁止** Agent 创建支付单、调支付、改 `credit_account`。
- 任何意图：**禁止** Agent 持有联盟密钥、支付密钥或直接调电商官方 SDK。

