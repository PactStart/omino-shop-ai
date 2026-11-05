# Contract: 助手调用

消费者路径不变：`POST /api/app/v1/chat/turns` 立即返回 `runId`，`GET /api/app/v1/chat/runs/{runId}/events` 读取事件，`POST .../cancel` 才停止。事件名以 [`docs/conversation.md`](../../../docs/conversation.md) 为准。

助手只增加已有内网入口的行为：`POST /internal/v1/graph/invoke`。请求字段以 [`docs/api-internal.md`](../../../docs/api-internal.md) §4.4 为准，本文件不另定义一套 JSON。

## 鉴权

请求必须带 `X-Internal-Token`，与助手进程的内网令牌一致。缺失或不符时返回 401，不进入图。助手不接受消费者令牌，不对公网监听。

## 购物服务必须送出的正文

用 JSON 库组装，不拼接字符串。

| 项 | 规则 |
| --- | --- |
| 模型对象 | 字段名是 `model`。发送本轮抽中的 `id`、`baseUrl`、`model`、`apiKey`、`timeoutMs` |
| 空模型 | 仅本地可以是 `null`。测试和生产在调用前就结束，不把空模型送进助手 |
| 提示词 | 八个键都在，正文为库里的当前值 |
| 链接、图片、恢复 | `sourceUrl`、`imageObjectKey`、`hitl` 按本轮输入送出，没有则为空 |
| 平台 | `installedPlatforms` 来自本轮设备；`authorizedPlatforms` 由购物服务查询该用户后填入 |
| 密钥 | `apiKey` 只出现在这条内网请求里 |

现有实现把模型字段写成 `modelName`，并省略链接、图片、恢复和真实授权平台。本功能改到与 §4.4 一致。

## 响应

`Content-Type: text/event-stream`。每条事件：

```text
event: <事件名>
data: <单行 JSON>

```

事件名只使用对话契约里的名字。新的一句先发 `intent`，再追问或文本，再卡片，最后恰好一个终态。授权恢复从暂停点继续，不再发一次 `intent`，也不再发一张授权卡或确认卡。超过 6 条商品时只有 `collection_card`，没有逐条 `product_card`。

`error.data` 只有 `code` 和 `message`。不包含堆栈、SQL、密钥或 PID。

## 用量

`usage` 在每次模型调用后发送。`promptTokens`、`completionTokens`、`totalTokens`、`credits` 都是本轮累计，`model` 等于本轮这一个模型名。应扣金额是现有公式作用于本轮累计 token 的结果，减去本轮已经扣过的金额。`0.01` 最低扣费不按每一小段另算。同一累计值再次到达不扣第二次。`done` 时按最终用量多退少补，余额不为负。助手没有改积分的工具。

## 取消与断开

购物服务发现取消标记后关闭这条响应。助手看到连接断开后取消正在运行的图，不再写入事件。已完成的检查点保留，半截节点不落盘。

消费者刷新只断开自己的阅读，购物服务不关闭这条内网调用。

## 追踪

生产环境 `LANGFUSE_ENABLED` 不为真时，助手拒绝进入图。其它环境关闭追踪时，图仍返回事件。追踪编号沿用请求里的 `traceId`。追踪送出前去掉 `apiKey`。异常日志只留状态码和追踪号，不打印调用正文。
