# Data Model: 002-java-api

表、列、索引以 [`docs/data-model.md`](../../docs/data-model.md) 为准。本文件只写本功能新增的状态规则，以及实现前必须补进那份权威文档的列。不另抄一套字段。

迁移用 Flyway，从 feat 1 只建 `vector` 扩展的库，补齐权威文档中的业务表。不使用 MySQL。不建 LangGraph 官方 checkpoint 表；那个表由后续对话功能的组件创建。`user_event` 与 `memory_semantic` 属于权威文档，本功能建表。浏览写入 `user_event.kind=browse`。

## 提现状态

`withdrawal.status` 在实现前补进权威文档，只允许：

| 状态 | 含义 |
| --- | --- |
| `pending` | 已冻结，尚未打款成功，也未拒绝 |
| `paid` | 打款成功，冻结额已扣减，已有一条提现流水 |
| `rejected` | 已解冻，没有提现流水 |

权威文档目前只写了 `pending`。`paid` 与 `rejected` 是本功能要补上的取值，不是第三套枚举。

同时补两列，没有它们无法保证打款只发生一次：

| 列 | 类型 | 约束 | 含义 |
| --- | --- | --- | --- |
| `provider_transfer_id` | VARCHAR(64) | 可空 | 只在打款成功后保存通道返回的单号。发给通道的幂等号永远是本行 `id`，不从本列读取。失败不写本列 |
| `paid_at` | TIMESTAMPTZ(3) | 可空 | 打款成功时刻 |

`channel` 本功能只允许 `alipay`。

## 状态转移

```text
申请（可用余额足够）
  available_amount -= amount
  frozen_amount += amount
  status = pending
  不写 wallet_ledger

拒绝（仅 pending）
  frozen_amount -= amount
  available_amount += amount
  status = rejected
  写入 reject_reason
  不写 wallet_ledger

打款失败（仅 pending）
  金额不动
  status 仍为 pending
  不写 wallet_ledger

打款成功（仅 pending，且通道确认成功）
  frozen_amount -= amount
  status = paid
  paid_at = now()
  写 wallet_ledger.type=withdraw，withdrawal_id 指向本行
```

已是 `paid` 的再次打款：不再调用通道，不写第二条流水。已是 `rejected` 的再次拒绝：成功且不重复解冻。非 `pending` 不能改去另一条终态。打款或拒绝前，先锁定该用户钱包行，再在同一事务里重读本行状态。只有仍为 `pending` 才继续。

打款调用的幂等号是 `withdrawal.id`。不得把 `provider_transfer_id` 当作下一次请求的幂等号。通道已成功但本地事务未提交时，重试必须带同一个 `withdrawal.id`，由通道拒绝第二次出款，本地再把仍为 `pending` 的行收成 `paid`，并在那时写入通道单号。失败不写 `provider_transfer_id`。改可用余额或冻结额的事务先锁定该用户钱包行。可用余额与冻结额不得为负。

## 钱包合计

`available_amount + frozen_amount` 只在两处变化：订单结算增加可用余额，打款成功减少冻结额。购积分的 `wallet` 渠道减少可用余额，并写 `wallet_ledger.type=credit_buy`。积分账户不参与提现。

消费者订单响应仍然不下发 `media_commission`、`parent_user_id`、`wallet_ledger_id`。

## 明确不改

- 不给上级写第二笔分成。
- 不增加冻结、解冻流水类型。
- 不把积分提现或积分转回钱包建模成新表。
- 不复制 mall-guide 的表名。
