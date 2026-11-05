# Contract: 工具与记忆

节点只能调用 [`docs/api-internal.md`](../../../docs/api-internal.md) §4.5 的白名单。请求头是 `X-Internal-Token` 与 `X-User-Id`。`X-User-Id` 等于本轮入口里的用户，节点不能改成别人。

本文件不复制每个路径的字段。下面是本功能必须遵守的调用边界，以及记忆写入这一处要先补进权威文档的增量。

## 谁可以调用什么

| 意图 | 允许 | 禁止 |
| --- | --- | --- |
| `shop.*` | 搜索、解析链接、解析图片、读取详情 | 转链、改钱包、改积分 |
| `order.query` / `me.stats` | 订单、统计、钱包只读 | 编造物流；回复媒体佣金 |
| `me.profile` | 资料、记忆检索 | 把未存储的内容说成事实 |
| `me.credits` | 读取积分 | 创建支付、改余额 |
| `auth.*` | 开始授权、取消授权 | 未确认就取消 |
| `earn.invite` | 邀请统计 | 改邀请关系 |
| `kb.faq` | 知识库检索 | 搜索商品；未命中时编政策 |
| `ticket.create` / `handoff` | 建立工单 | 搜索商品 |
| `chitchat` | 无 | 任何业务工具 |

单次工具超时 5 秒。失败时说明暂时失败或搜不到，不编造商品、单号和金额。订单同步路径不是工具，助手不调用它。

## 搜品槽位

预算和平台以这句话为准。这两项为空时，使用该用户未过期的 `explicit` 记忆。`inferred` 候选不参与搜索。关键词为空时追问，不用记忆编关键词。

## 卡片

搜索返回不超过 6 条时，事件使用商品卡。返回合集编号时，只发送合集卡，封面最多 4 张。销量、评价数为空就保持为空。`goodsUrl` 只是商品页，不是购买动作。

## 记忆检索（已有路径，补实现）

`POST /internal/v1/memory/search`

今天固定返回空列表。本功能改为返回当前用户的记忆。

请求体：

```json
{ "limit": 20 }
```

`limit` 缺省 20，最大 20。响应 `data` 是数组，元素含 `id`、`kind`、`text`、`confidence`、`source`、`createdAt`。只含 `valid_to` 为空的行。不返回其它用户的行，不返回 `embedding`。

## 记忆写入（先改权威文档）

实现前把下面这条补进 [`docs/api-internal.md`](../../../docs/api-internal.md) 的工具表。不在代码里先开路径。

`POST /internal/v1/memory`

| 项 | 规则 |
| --- | --- |
| 调用者 | 记忆节点。不放进模型可自由挑选的工具 |
| 请求体 | `kind`、`text`、`confidence`、`source`，以及可选的 `supersedeId` |
| source | `explicit` 或 `inferred`。`survey` 拒绝 |
| confidence | 0 到 1。`inferred` 低于 0.8 时只插入，不覆盖 |
| supersedeId | 必须属于当前用户。成功时给该行写上 `valid_to`，再插入新行 |
| 服务端填写 | 用户编号、主键、创建时间。`embedding` 留空 |
| 拒绝 | 缺正文、把握越界、指定了别人的记忆 |

客服、查账和工单节点不调用这条。

## 用量

`usage` 不是工具。它是助手给购物服务的事件。购物服务按 [`docs/conversation.md`](../../../docs/conversation.md) 的比例扣本轮增量。助手没有对应的写入工具。
