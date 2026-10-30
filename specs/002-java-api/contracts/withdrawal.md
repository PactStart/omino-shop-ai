# 返佣提现

实现前把本文件写入 [`docs/api-app.md`](../../../docs/api-app.md) 与 [`docs/api-admin.md`](../../../docs/api-admin.md)。状态列以 [data-model.md](../data-model.md) 为准，并同时补进 [`docs/data-model.md`](../../../docs/data-model.md)。

金额为字符串、两位小数。收款账号完整值只留在服务端用于打款。列表和详情只返回 `accountMask`。日志、审计、对话工具响应不写完整账号。

本功能 `channel` 只能是 `alipay`。

## 消费者（APP JWT）

| 方法 | 路径 | 行为 |
| --- | --- | --- |
| GET | `/api/app/v1/wallet` | 本人 `availableAmount`、`frozenAmount`、`totalEarned` |
| POST | `/api/app/v1/withdrawals` | body：`amount`、`payeeAccount`。`amount` 必须大于 0 且不超过可用余额，否则 `40001`。成功后状态 `pending`，可用余额减少，冻结额增加 |
| GET | `/api/app/v1/withdrawals` | 本人分页 |
| GET | `/api/app/v1/withdrawals/{id}` | 本人一笔。别人的返回 `40400` |

无 JWT：HTTP `401`，`code` 为 `40100`。

## 运营（`sid_admin`）

| 方法 | 路径 | 行为 |
| --- | --- | --- |
| GET | `/api/admin/v1/withdrawals` | 分页。query：`userId`、`status`、`page`、`size` |
| POST | `/api/admin/v1/withdrawals/{id}/payout` | 仅 `pending` 会打款。成功则 `paid` 并写一条 `wallet_ledger.type=withdraw`。失败则仍是 `pending`，冻结额不动，响应 `code` 仍为 `0`，`message` 为「打款未成功，冻结额未动」，`data.payoutResult` 为 `failed`。已 `paid` 的再次调用不再打款 |
| POST | `/api/admin/v1/withdrawals/{id}/reject` | body：`reason`。仅 `pending` 会解冻并变为 `rejected`。已拒绝再调用保持 `rejected` |

未登录按 [admin-session.md](admin-session.md) 返回 `401` / `40100`。

不新增错误码。打款失败不是 `50010`。积分没有提现路径。
