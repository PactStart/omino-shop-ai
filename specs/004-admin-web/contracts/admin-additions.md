# 管理接口增补

页面要的、而 [`docs/api-admin.md`](../../../docs/api-admin.md) 还没有的能力。实现前把下表写进该文档，再写代码。信封、未登录 `401` / `40100`、分页形状与现有管理接口相同。

已在该文档里、但进程还没做的方法不在这里重复，包括推荐问题的 `GET` 与 `PATCH`、订单详情、积分比例 / 支付单 / 流水 / 调账、海报与版本等的查询和更新。那些按原契约补齐。

## 工作台

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/dashboard` | `syncFailures`、`ticketCount`、`pendingWithdrawals`。三个整数都来自 [data-model.md](../data-model.md) 的查询。没有行时为 0 |

## 会话（只读）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/conversations` | 分页。query：`userId` `page` `size`。行含 `id` `userId` `title` `lastIntent` `updatedAt` |
| GET | `/api/admin/v1/conversations/{id}/messages` | 按时间升序。含 `role` `content` `intent` `createdAt`。不返回可写操作 |

响应不含模型密钥和联盟 PID。

## 工单

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/tickets` | 分页。query：`userId` `status` `page` `size` |
| GET | `/api/admin/v1/tickets/{id}` | 工单当前值加处理记录时间线 |
| POST | `/api/admin/v1/tickets/{id}/reply` | body：`content`。只追加 `reply`。状态不变。已关闭则 `40001` |
| PATCH | `/api/admin/v1/tickets/{id}` | 可选 `status` `priority` `assigneeSsoSub`。改了哪一项就追加对应的处理记录。`status=closed` 才是关单 |

## 知识库

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/kb/articles` | 含 `retired` |
| POST | `/api/admin/v1/kb/articles` | body：`title` `body`。新建为 `published` |
| PATCH | `/api/admin/v1/kb/articles/{id}` | 可改标题、正文、`status`（`published` 或 `retired`）。没有删除 |

`retired` 不再被现有知识库检索返回。

## 审计与钱包流水

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/audit-logs` | 分页，时间倒序。query：`page` `size`。行含 `ssoSub` `action` `resource` `createdAt`。`detail` 若有，也不含密钥、PID、完整收款账号 |
| GET | `/api/admin/v1/wallet-ledger` | 分页。query：`userId` `page` `size`。用户详情必须带 `userId`。金额为两位小数字符串 |

## 同步游标

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/order-sync` | 固定四行：`taobao` `jd` `pdd` `vip`。每行 `lastSyncCreateTime` `lastSyncUpdateTime` `lastError` `updatedAt` |
| POST | `/api/admin/v1/order-sync/{platform}/retry` | 平台不是这四个则 `40001`。写审计 `action=order.sync.retry`，再触发 APP 上一次该平台的既有增量。失败时 `lastError` 仍是固定文案，不含密钥 |

APP 侧触发路径（不是 Agent 工具）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/internal/v1/order-sync/{platform}` | 仅内部令牌。调用现有单平台增量。不加入 `docs/api-internal.md` 的工具白名单 |

## 登录回跳

`GET /api/admin/v1/auth/callback` 在会话建立后 `302` 到管理前端源的 `/`。失败不发 `sid_admin`，前端 `/login/callback` 只显示可重登。响应和地址栏不出现 access_token。
