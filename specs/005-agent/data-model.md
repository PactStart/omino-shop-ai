# Data Model: 005-agent

本功能不新增业务表，不修改已有列，不写 Flyway。字段以 [`docs/data-model.md`](../../docs/data-model.md) 为准。这里只写这一轮会读、会写的对象和状态。

## 工作记忆

官方 `PostgresSaver.setup()` 创建的 checkpoint 表。`thread_id` 等于 `conversation.id`。

保存：槽位、未执行的意图、本轮商品编号、授权暂停编号。不保存向量，不保存模型密钥。

取消发生在节点中途时，不写入半截节点。下一句使用新的一轮编号和同一个会话编号，从上一份完整检查点继续。

授权恢复不是从入口重跑。同一会话线程提交恢复值，从暂停点继续，不进入意图路由。

## 一轮输入

购物服务在调用助手前凑齐这些字段。它们来自已有开一轮契约，今天还没有全部传到助手。

| 字段 | 规则 |
| --- | --- |
| traceId | 这一次 HTTP 的追踪号 |
| userId / conversationId / clientMsgId | 租户和幂等 |
| inputType | `text`、`voice_transcript`、`goods_url`、`image` |
| text | 文本和语音转写且没有恢复动作时必填。`hitl` 非空、商品链接、商品图可以没有文本 |
| sourceUrl | 仅 `goods_url` |
| imageObjectKey | 仅 `image` |
| hitl | 空，或带 `interruptId` 与 `action` 的恢复 |
| installedPlatforms | 客户端上报的已安装平台，只接受四个平台码 |
| authorizedPlatforms | 购物服务按该用户现有授权填入 |
| prompts | 八个固定键，正文非空 |
| llm | 本轮抽中的一条；本地可以没有 |

同一 `conversationId` + `clientMsgId` 已受理时不新开图。

## 图状态

名称以实现时的类型为准，语义锁定。

| 字段 | 谁写 | 含义 |
| --- | --- | --- |
| 租户与追踪 | 入口 | 用户、会话、追踪号 |
| 本轮输入 | 入口 | 文本、链接、图片、恢复载荷、已安装和已授权平台 |
| intent / confidence / slots | 路由 | 一个主意图、把握、槽位 |
| pending_intents | 路由 | 这句话里留到下一句的意图 |
| referents | 指代 | 已绑定的商品或订单编号 |
| clarify | 追问 | 问题与缺的槽位 |
| goods | 导购 | 准备下发的卡或合集 |
| ticket | 客服 | 新建工单号 |
| interrupt | 授权 | 暂停编号与平台 |

## 意图路由

取值只允许 [`docs/conversation.md`](../../docs/conversation.md) 的点分名。未知值进入闲聊或加急工单。

| 条件 | 结果 |
| --- | --- |
| 把握低于 0.6 | 追问，本轮工具次数为 0 |
| `shop.*` 且槽位不足 | 追问，不搜 |
| `shop.search` / `shop.gift` / `shop.scene` 且槽位够 | 搜索。预算和平台以这句话为准，为空时用未过期的明确记忆。不超过 6 条下发商品卡；超过 6 条只下发合集卡 |
| `shop.link` | 一张商品卡，或说明链接打不开 |
| `shop.image` | 一张卡，或需要继续找时遵守 6 条规则；认不出则追问 |
| `shop.compare` | 只解释已绑定的商品，不重新大范围搜索 |
| `auth.grant` / `auth.revoke` | 授权卡或确认卡，然后暂停 |
| `order.query` | 只读订单。物流空则说未知 |
| `me.*` / `earn.invite` | 只读账户、积分、邀请 |
| `kb.faq` | 只检索知识库。未命中则说明并引导建单 |
| `ticket.create` / `handoff` | 建普通或加急工单 |
| `chitchat` | 短文本，无业务工具 |

一句多意图只执行主意图。

## 长期偏好 `memory_semantic`

不改表。助手不直接写这张表。

| 规则 | 行为 |
| --- | --- |
| 读取 | 仅 `X-User-Id` 对应的、`valid_to` 为空的行，最多 20 条 |
| `inferred` 且把握低于 0.8 | 插入候选，不覆盖已有行 |
| `explicit` | 插入正式记忆 |
| 矛盾的厌恶 | 旧行写入 `valid_to`，再插入新行 |
| `survey` | 本功能不写。问卷仍走原有资料接口 |
| embedding | 本功能保持空 |
| 客服、查账、工单 | 不写 |

`app_user_preference` 只经现有资料接口读取，本功能不更新它的列。

## 助手不写的表

`user_event`、`conversation`、`conversation_message`、`app_order`、`wallet`、`wallet_ledger`、`credit_account`、`credit_ledger`、`goods_snapshot`。

商品快照和合集仍由搜索工具在购物服务里写入。对话消息仍由购物服务在开跑前和终态后写入。

## 终态

`done`、`error`、`cancelled`、`credit_exhausted` 四者互斥，一轮只有一个。

| 终态 | 何时 |
| --- | --- |
| done | 图正常结束，或授权暂停后本轮先结束、等待下一句 |
| error | 助手不可用、上游失败、或测试/生产缺少提示词和模型 |
| cancelled | 用户点停止。已写出的文字保留 |
| credit_exhausted | 开跑前或流中余额不足。助手不发起支付 |

刷新和断开阅读不产生 `cancelled`。
