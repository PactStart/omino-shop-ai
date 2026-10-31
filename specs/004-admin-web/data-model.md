# Data Model: 004-admin-web

本功能不新增表，不改列约束。字段以 [`docs/data-model.md`](../../docs/data-model.md) 为准。这里只记录页面会读和写的对象，以及实现前要对齐的一处注释。

## 不改的对象

| 对象 | 页面怎么用 | 写入规则 |
| --- | --- | --- |
| `conversation` / `conversation_message` | 会话列表和消息时间线，可按 `user_id` 筛选 | 后台只读。不改用户消息 |
| `ticket` / `ticket_log` | 列表、详情、回复、改派、改状态和优先级 | 变更与一条 `ticket_log` 同一事务。`reply` 不改 `status`。关单才把状态写成 `closed` 并记 `status_change`。日志不改历史 |
| `kb_article` | 列表和编辑 | 下架只把 `status` 改为 `retired`。不物理删除。检索继续只命中 `published` |
| `admin_audit_log` | 审计表；提示词、模型、调账、上帝视角、同步重试继续写 | 只追加。`detail` 不含密钥、PID、完整收款账号 |
| `order_sync_cursor` | 四行水位和 `last_error` | 重试不直接改这两列。水位仍只由 APP 里现有的增量事务推进 |
| `chat_suggestion` | 全部列出，含 `retired`；改文案、排序、上下架 | 同时 `active` 最多 12。不物理删除 |
| `agent_prompt` | 八条固定 key，只改 `content` | 不增删 key。去空白后不得为空 |
| `llm_model` | 改权重等；密钥只在填写时提交 | 读出只有是否已设置和末四位 |
| `app_order` | 筛选列表和详情 | 后台只读。展示 `media_commission` 与 `user_commission` |
| `wallet` / `wallet_ledger` | 用户详情的余额和流水 | 流水只读。不在后台改历史行 |
| `withdrawal` | 审核 | 列表只有 `account_mask`。打款和拒绝沿用现有规则 |
| `invite_poster_template` | 模板、矩形、生效时间 | 不产生合成图 |
| `credit_package` / `credit_order` / `credit_ledger` | 套餐、支付单、流水、调账 | 调账写 `type=adjust` 并记审计。被引用的套餐不物理删除 |
| `app_release` / `app_runtime_config` / `announcement` / `inbox_message` | 版本、特殊配置、公告、站内信 | 站内信必须有一个 `user_id`。版本同端同渠道不插第二条同日记录 |

## 状态

工单：`open` → `processing` → `closed`，也允许从 `open` 直接到 `closed`。已 `closed` 不再被回复重新打开。本功能不增加第四个状态。

知识库：`published` ↔ `retired`。没有草稿态。

推荐问题：`active` ↔ `retired`。第 13 条要变成 `active` 时拒绝，原状态保持。

## 实现前改文档的一处

`docs/data-model.md` 里 `kb_article.status` 的注释目前写成 `active`。实体枚举是 `published` 与 `retired`。实现前把注释改成这两个取值，不迁移数据，不改列类型。

## 工作台计数

不建汇总表。三个数各查一次：

- 同步失败：`order_sync_cursor.last_error` 非空的平台数，分母只有四个平台。
- 开单量：`ticket` 行数。
- 待审提现：`withdrawal.status = pending` 的行数。

查到 0 就返回 0。
