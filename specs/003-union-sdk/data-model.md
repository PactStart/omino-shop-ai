# Data Model: 003-union-sdk

本功能不新增表，不改列。表结构以 [`docs/data-model.md`](../../docs/data-model.md) 为准。这里只写官方结果怎样填进既有列。

## 不改的对象

- `goods_snapshot`：仍是瘦快照，不是全量商品库。
- `platform_authorization`：转链前仍只看用户是否已授权该平台。
- `click_event`：只在转链成功后写入。失败不写。
- `app_order`、`wallet`、`wallet_ledger`：入账条件、六种 `order_status`、三种 `settle_status`、一次入账、不给上级写第二笔，都保持 feat 2。
- `order_sync_cursor`：仍是每个平台一行，两列水位 `last_sync_create_time`、`last_sync_update_time`。

## 官方结果的填法

| 既有列 | 规则 |
| --- | --- |
| `sales_count` / `review_count` | 官方没给或无法解析：空。官方给出的数字按原值写入，包括 0 |
| `goods_url` | 平台商品页。不是转链结果，也不是 deeplink |
| 转链响应 | 可购买链接来自官方转链。响应字段仍是现有契约，不增加 PID、relationId、密钥 |
| `order_status` | 只写待支付、待发货、已发货、已完成、已取消、未知。原码对照参考实现的状态表：表里的待付款写成待支付，已付款写成待发货，已发货写成已发货，已完成写成已完成，已取消写成已取消。表里落到待付款的空值和其余原码也写成待支付。参考实现的已收货在这六态里没有对应项，写成已发货，不写成已完成 |
| `settle_status` 与 `wallet_ledger` | 仍由 `OrderSyncService` 决定。适配层只提供尚未落库的订单 |
| `last_error` | 该平台本次拉取失败时写入固定文案，不含密钥、PID、relationId 和官方原文。成功推进水位时清空 |
| 两列水位 | 拉取失败时两列都停在原处。拉取成功后，仍在同一事务里前进 |

## 状态

订单状态与结算状态的转移不在本功能改写。官方调用的终态有三种：映射进上表、官方正常返回的空结果（算成功拉取，不算正向验收）、整次调用失败（`50010`，不写点击、不推进水位）。
