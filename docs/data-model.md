# 数据模型（PostgreSQL 16 + pgvector）

> 本文件是表结构的权威正文。意图与记忆用法见 [`conversation.md`](./conversation.md)。接口字段见 `api-*.md`。

## 6. 数据模型

引擎：**PostgreSQL 16 + pgvector**。主键默认 `CHAR(26)` ULID。时间 `TIMESTAMPTZ(3)`，应用层写 UTC。软删 `deleted_at` 可空。金额 `NUMERIC(12,2)`。JSON 用 `JSONB`。向量列 `vector(1536)`（与嵌入模型维度一致，换模型须改本列并重灌）。

平台枚举（全表共用，仅此四者）：`taobao` `jd` `pdd` `vip`。**禁止** `meituan`。未接的四者表结构可先预留，适配器按阶段落地。

商品**全量原始报文不进业务库**；`goods_snapshot` 仅为对外 `goodsId` 与列表/详情瘦字段，热路径以 Redis 为准。

### 6.1 `app_user` 终端用户


| 字段          | 类型             | 约束                     | 注释                      |
| ----------- | -------------- | ---------------------- | ----------------------- |
| id          | CHAR(26)       | PK                     | 用户主键（ULID）              |
| nickname    | VARCHAR(64)    | NOT NULL，默认 `用户`       | 展示昵称                    |
| avatar_url  | VARCHAR(512)   | 可空                     | 头像 URL                  |
| phone       | VARCHAR(20)    | 可空，UNIQUE（非空时）         | 绑定手机号，未绑定时为空            |
| status      | TINYINT        | NOT NULL，`1` 正常 `0` 禁用 | 账号状态                    |
| locale      | VARCHAR(16)    | NOT NULL，默认 `zh-CN`    | 界面与对话语言                 |
| invite_code | CHAR(8)        | NOT NULL，UNIQUE        | 邀请码，注册时生成，不可改；字母表见 [`api-app.md`](./api-app.md) §4.7 |
| inviter_id  | CHAR(26)       | 可空，FK → app_user.id   | 邀请人（上级）。注册绑定邀请码时写入，之后不可改；无邀请则为空；禁止等于自身。与 `app_invite.inviter_id` 同时写入、必须一致 |
| push_enabled | TINYINT       | NOT NULL，默认 `1`        | 站内/系统推送总开关；`1` 开 `0` 关     |
| created_at  | TIMESTAMPTZ(3) | NOT NULL               | 创建时间（UTC）；「我何时注册」读此列    |
| updated_at  | TIMESTAMPTZ(3) | NOT NULL               | 最后更新时间（UTC）             |
| deleted_at  | TIMESTAMPTZ(3) | 可空                     | 软删时间，空表示未删除             |


### 6.2 `app_user_identity` 第三方登录绑定


| 字段                             | 类型             | 约束                        | 注释                |
| ------------------------------ | -------------- | ------------------------- | ----------------- |
| id                             | CHAR(26)       | PK                        | 绑定记录主键            |
| user_id                        | CHAR(26)       | NOT NULL，FK → app_user.id | 所属用户              |
| provider                       | VARCHAR(16)    | NOT NULL，`wechat`         | `phone`           |
| provider_uid                   | VARCHAR(128)   | NOT NULL                  | 渠道侧用户唯一 ID        |
| union_id                       | VARCHAR(128)   | 可空                        | 微信 UnionID，其它渠道为空 |
| created_at                     | TIMESTAMPTZ(3) | NOT NULL                  | 绑定时间              |
| UNIQUE(provider, provider_uid) |                |                           | 同一渠道账号只能绑一个用户     |


### 6.3 `app_user_preference` 长期偏好（记忆·长期）


| 字段                  | 类型             | 约束                 | 注释                 |
| ------------------- | -------------- | ------------------ | ------------------ |
| id                  | CHAR(26)       | PK                 | 偏好记录主键             |
| user_id             | CHAR(26)       | NOT NULL，UNIQUE，FK | 一用户一行              |
| preferred_platforms | JSONB          | NOT NULL，默认 `[]`   | 偏好平台码数组，如 `["jd"]` |
| budget_max          | NUMERIC(12,2)  | 可空                 | 常用预算上限（元）          |
| disliked_keywords   | JSONB          | NOT NULL，默认 `[]`   | 不想看到的关键词           |
| extra               | JSONB          | NOT NULL，默认 `{}`   | 扩展偏好，避免频繁加列        |
| updated_at          | TIMESTAMPTZ(3) | NOT NULL           | 最后更新时间             |


### 6.4 `app_user_installed_app` 已安装电商 APP 快照


| 字段                        | 类型             | 约束          | 注释              |
| ------------------------- | -------------- | ----------- | --------------- |
| id                        | CHAR(26)       | PK          | 快照主键            |
| user_id                   | CHAR(26)       | NOT NULL，FK | 所属用户            |
| platform                  | VARCHAR(16)    | NOT NULL    | 白名单平台码          |
| installed                 | TINYINT        | NOT NULL    | `1` 已安装 `0` 未安装 |
| reported_at               | TIMESTAMPTZ(3) | NOT NULL    | APP 最近一次上报时间    |
| UNIQUE(user_id, platform) |                |             | 每用户每平台一行        |


只存白名单平台，不存完整装机列表。

### 6.5 `conversation` / `conversation_message`

**conversation**


| 字段          | 类型             | 约束          | 注释                             |
| ----------- | -------------- | ----------- | ------------------------------ |
| id          | CHAR(26)       | PK          | 会话 ID，即 API 的 `conversationId` |
| user_id     | CHAR(26)       | NOT NULL，FK | 所属用户                           |
| title       | VARCHAR(128)   | 可空          | 会话标题，可由首轮用户话自动生成               |
| last_intent | VARCHAR(64)    | 可空          | 最近一次一级意图（点分名）                  |
| created_at  | TIMESTAMPTZ(3) | NOT NULL    | 创建时间                           |
| updated_at  | TIMESTAMPTZ(3) | NOT NULL    | 最后一条消息时间                       |


**conversation_message**


| 字段                                     | 类型             | 约束              | 注释                     |
| -------------------------------------- | -------------- | --------------- | ---------------------- |
| id                                     | CHAR(26)       | PK              | 服务端消息 ID               |
| conversation_id                        | CHAR(26)       | NOT NULL，FK     | 所属会话                   |
| client_msg_id                          | VARCHAR(64)    | NOT NULL        | 客户端幂等键                 |
| role                                   | VARCHAR(16)    | NOT NULL，`user` | `assistant`            |
| content                                | TEXT           | NOT NULL        | 文本内容                   |
| intent                                 | VARCHAR(64)    | 可空              | 本轮一级意图                 |
| payload                                | JSONB          | 可空              | 商品卡、槽位、HITL            |
| created_at                             | TIMESTAMPTZ(3) | NOT NULL        | 入库时间                   |
| UNIQUE(conversation_id, client_msg_id) |                |                 | 同一会话内 `clientMsgId` 幂等 |


### 6.6 记忆：Checkpointer + 事件 + 语义向量

**Working**：不另建业务表。使用 LangGraph `PostgresSaver`（官方 checkpoint 表），`thread_id = conversation.id`。存槽位、本轮 `goodsId[]`、`pendingIntents`、HITL `interruptId`。

**Episodic — `user_event`**


| 字段                               | 类型             | 约束               | 注释       |
| -------------------------------- | -------------- | ---------------- | -------- |
| id                               | CHAR(26)       | PK               | 事件主键     |
| user_id                          | CHAR(26)       | NOT NULL，FK      | 所属用户     |
| kind                             | VARCHAR(32)    | NOT NULL         | `browse` \| `click` \| `order` \| `auth` |
| platform                         | VARCHAR(16)    | 可空               | 平台       |
| goods_id                         | CHAR(26)       | 可空               | 本系统商品 id |
| platform_goods_id                | VARCHAR(64)    | 可空               | 平台商品 id  |
| order_id                         | CHAR(26)       | 可空               | 关联订单     |
| title                            | VARCHAR(512)   | 可空               | 展示摘要     |
| payload                          | JSONB          | NOT NULL，默认 `{}` | 价格等最小字段  |
| created_at                       | TIMESTAMPTZ(3) | NOT NULL         | 事件时间     |
| INDEX(user_id, kind, created_at) |                |                  | 近况召回     |


浏览列表读 `kind=browse`（进详情时写入）。摘要保留 90 天，定时物理删。收藏走 `goods_favorite`，不写本表。

**Semantic — `memory_semantic`**（开放表述；结构化槽位仍以 `app_user_preference` 为准）


| 字段                             | 类型             | 约束          | 注释                                               |
| ------------------------------ | -------------- | ----------- | ------------------------------------------------ |
| id                             | CHAR(26)       | PK          |                                                  |
| user_id                        | CHAR(26)       | NOT NULL，FK |                                                  |
| kind                           | VARCHAR(32)    | NOT NULL    | 如 `budget` / `size` / `hate_brand` / `free_text` |
| text                           | TEXT           | NOT NULL    | 可检索原文                                            |
| embedding                      | vector(1536)   | 可空          | pgvector；结构化列不必写向量                               |
| confidence                     | NUMERIC(3,2)   | NOT NULL    | 0–1；低于 0.8 为候选                                   |
| source                         | VARCHAR(16)    | NOT NULL    | `survey`                                         |
| valid_to                       | TIMESTAMPTZ(3) | 可空          | 过期或被「以后别…」覆盖时赋值                                  |
| created_at                     | TIMESTAMPTZ(3) | NOT NULL    |                                                  |
| INDEX ivfflat/hnsw (embedding) |                |             | 按 user 过滤后向量检索                                   |


问卷与「记住我以后…」→ `source=survey/explicit` 且可覆盖 inferred。

### 6.7 `goods_snapshot` 瘦快照（非全量商品库）


| 字段                                  | 类型             | 约束       | 注释                        |
| ----------------------------------- | -------------- | -------- | ------------------------- |
| id                                  | CHAR(26)       | PK       | 对外 `goodsId`              |
| platform                            | VARCHAR(16)    | NOT NULL | 电商平台码                     |
| platform_goods_id                   | VARCHAR(64)    | NOT NULL | 平台侧商品 ID                  |
| platform_goods_alias                | VARCHAR(64)    | 可空       | 跨平台去重辅助 ID（同款在不同平台 ID 不同） |
| title                               | VARCHAR(512)   | NOT NULL | 商品标题                      |
| image_url                           | VARCHAR(1024)  | 可空       | 主图                        |
| price                               | NUMERIC(12,2)  | 可空       | 原价（元）                     |
| coupon_price                        | NUMERIC(12,2)  | 可空       | 券后价（元）                    |
| shop_name                           | VARCHAR(256)   | 可空       | 店铺名                       |
| sales_count                         | BIGINT         | 可空       | 销量。平台没给则为空；禁止把未知写成 `0`   |
| review_count                        | INT            | 可空       | 评价数。平台没给则为空；禁止把未知写成 `0` |
| goods_url                           | VARCHAR(1024)  | 可空       | 平台商品页链接。不是转链、不是 deeplink；去购买仍走 `convert-link` |
| raw_hash                            | CHAR(64)       | 可空       | 上游摘要；禁止存完整原始报文            |
| updated_at                          | TIMESTAMPTZ(3) | NOT NULL | 快照刷新时间                    |
| UNIQUE(platform, platform_goods_id) |                |          | 同一平台商品只保留一行快照             |


### 6.7.1 `goods_collection` 对话/搜品合集

搜品结果 **多于 6 条** 时由 Java 写入，供 SSE `collection_card` 与合集页。


| 字段 | 类型 | 约束 | 注释 |
| --- | --- | --- | --- |
| id | CHAR(26) | PK | 对外 `collectionId` |
| user_id | CHAR(26) | NOT NULL，FK | 仅本人可读/改 |
| conversation_id | CHAR(26) | 可空 | 来源会话 |
| run_id | CHAR(26) | 可空 | 来源 run |
| title | VARCHAR(128) | NOT NULL | 如「纸巾推荐」 |
| source | VARCHAR(16) | NOT NULL | `search` |
| created_at | TIMESTAMPTZ(3) | NOT NULL | |
| INDEX(user_id, created_at) | | | |


### 6.7.2 `goods_collection_item`


| 字段 | 类型 | 约束 | 注释 |
| --- | --- | --- | --- |
| id | CHAR(26) | PK | |
| collection_id | CHAR(26) | NOT NULL，FK | |
| goods_id | CHAR(26) | NOT NULL | → `goods_snapshot.id` |
| sort | INT | NOT NULL，默认 0 | 列表序 |
| removed_at | TIMESTAMPTZ(3) | 可空 | 用户移除；空=仍在合集 |
| created_at | TIMESTAMPTZ(3) | NOT NULL | |
| UNIQUE(collection_id, goods_id) | | | |


列表与卡片只展示 `removed_at IS NULL`。移除不删快照、不改其它合集。

### 6.7.3 `goods_favorite` 收藏


| 字段 | 类型 | 约束 | 注释 |
| --- | --- | --- | --- |
| id | CHAR(26) | PK | |
| user_id | CHAR(26) | NOT NULL，FK | |
| goods_id | CHAR(26) | NOT NULL | |
| created_at | TIMESTAMPTZ(3) | NOT NULL | |
| UNIQUE(user_id, goods_id) | | | 再藏幂等 |


### 6.8 `app_order` 统一订单


| 字段                                  | 类型             | 约束       | 注释                        |
| ----------------------------------- | -------------- | -------- | ------------------------- |
| id                                  | CHAR(26)       | PK       | 本系统订单 ID                  |
| user_id                             | CHAR(26)       | 可空       | 未归因时为空                    |
| platform                            | VARCHAR(16)    | NOT NULL | 下单平台                      |
| platform_order_id                   | VARCHAR(64)    | NOT NULL | 平台订单号                     |
| goods_id                            | CHAR(26)       | 可空       | 归因商品                      |
| goods_title                         | VARCHAR(512)   | NOT NULL | 同步时标题快照                   |
| goods_image_url                     | VARCHAR(1024)  | 可空       | 同步时主图；列表/详情展示用，禁止存平台原始大图包 |
| pay_amount                          | NUMERIC(12,2)  | 可空       | 实付（元）                     |
| parent_user_id                      | CHAR(26)       | 可空，FK → app_user.id | 下单用户的上级，首次归因写入 `user_id` 时从 `app_user.inviter_id` 拷贝，之后不改。不是平台父订单号。可空（用户无邀请人） |
| media_commission                    | NUMERIC(12,2)  | 可空       | 电商平台付给本媒体的返佣（元），同步时覆盖为平台当前值。未回传为空。已结算后冻结 |
| user_commission                     | NUMERIC(12,2)  | 可空       | 本平台分给**下单用户**的返佣（元）= `round(media_commission × 分成比例, 2)`。已结算后冻结。消费者只看这一列 |
| click_id                            | CHAR(26)       | 可空       | 转链点击，见 `click_event`      |
| order_status                        | VARCHAR(32)    | NOT NULL | 订单状态，仅此六值：`pending_pay` 待支付、`pending_ship` 待发货、`shipped` 已发货、`completed` 已完成、`cancelled` 已取消、`unknown` 未知。平台原码由适配器映射，映射不上写 `unknown` |
| settle_status                       | VARCHAR(16)    | NOT NULL | 结算状态，仅此三值：`none` 不结算、`pending` 待结算、`settled` 已结算。与订单状态分开 |
| wallet_ledger_id                    | CHAR(26)       | 可空，UNIQUE，FK → wallet_ledger.id | 结算入账后的钱包流水。空=尚未入账。一单至多一条 |
| ordered_at                          | TIMESTAMPTZ(3) | 可空       | 平台下单时间                    |
| shipped_at                          | TIMESTAMPTZ(3) | 可空       | 有则用于「发货了吗」；无则如实说未知        |
| settled_at                          | TIMESTAMPTZ(3) | 可空       | 写入钱包流水的时间；未结算为空         |
| updated_from_platform_at            | TIMESTAMPTZ(3) | 可空       | 最近从平台更新                   |
| created_at                          | TIMESTAMPTZ(3) | NOT NULL | 本库首次写入                    |
| UNIQUE(platform, platform_order_id) |                |          | 平台订单号幂等                   |


归因：转链写入 `click_event`，同步订单时按推广位/click 回填 `user_id`，并同时拷贝 `parent_user_id`。禁止把联盟原始 JSON 进业务库。

原列 `status`、`commission_estimate` 删除，分别由 `order_status` 与 `media_commission` / `user_commission` 代替。`settled` 不再是订单状态。

**分成**：比例在 Nacos `commission.user-share-rate`（字符串小数，默认 `0.80`，范围 `(0, 1]`，热更新）。`user_commission = round(media_commission × rate, 2)`，`HALF_UP`。`media_commission` 为空则 `user_commission` 为空。改比例**不**回写已结算行；未结算行在下次同步按新比例重算。

**结算状态**（未入账时每次同步重算）：

| settle_status | 条件 |
| --- | --- |
| `none` | 无 `user_id`，或 `order_status` 为待支付 / 已取消 / 未知，或媒体返佣为空 / ≤0 |
| `pending` | 已归因，订单为待发货 / 已发货 / 已完成，媒体返佣 > 0，且尚未入账 |
| `settled` | 已写入钱包流水 |

**入账**（订单同步，同一事务，只发生一次）：`order_status=completed` 且 `settle_status=pending` 且 `user_commission > 0` 且 `wallet_ledger_id` 为空。写入一条 `wallet_ledger`（`type=commission`，`amount=user_commission`，`user_id` 为下单用户，`order_id` 为本单），增加该用户 `wallet.available_amount` 与 `total_earned`，回填 `wallet_ledger_id`，`settle_status=settled`，`settled_at=now()`。已有流水则跳过。

本阶段**不**给上级写第二笔流水，也没有上级分成金额。`parent_user_id` 只做归因。已结算后平台改口或订单变取消：**不自动追回**；要冲正则另做调账，不在同步里改历史流水。

消费者接口不下发 `media_commission`、`parent_user_id`、`wallet_ledger_id`。这三列只在 Admin。

### 6.9 `order_sync_cursor` 平台同步游标


| 字段                    | 类型             | 约束       | 注释               |
| --------------------- | -------------- | -------- | ---------------- |
| platform              | VARCHAR(16)    | PK       | 一个平台一行游标         |
| last_sync_create_time | TIMESTAMPTZ(3) | 可空       | 按下单时间增量拉取的水位     |
| last_sync_update_time | TIMESTAMPTZ(3) | 可空       | 按更新时间增量拉取的水位     |
| last_error            | VARCHAR(512)   | 可空       | 最近一次同步失败摘要，成功后清空 |
| updated_at            | TIMESTAMPTZ(3) | NOT NULL | 游标最后改动时间         |


逻辑（参考 mall-guide）：每平台独立游标；按「创建时间 / 更新时间」两条增量拉单；写入统一 `app_order`。调度在 Java 的 Quartz，间隔默认 5 分钟。

### 6.10 `click_event` 转链点击归因


| 字段                | 类型             | 约束          | 注释                 |
| ----------------- | -------------- | ----------- | ------------------ |
| id                | CHAR(26)       | PK          | 即 `click_id`       |
| user_id           | CHAR(26)       | NOT NULL，FK | 点击用户               |
| platform          | VARCHAR(16)    | NOT NULL    |                    |
| goods_id          | CHAR(26)       | 可空          |                    |
| platform_goods_id | VARCHAR(64)    | NOT NULL    |                    |
| created_at        | TIMESTAMPTZ(3) | NOT NULL    | convert-link 成功时写入 |


### 6.11 `platform_authorization` 联盟授权


| 字段                        | 类型             | 约束          | 注释                  |
| ------------------------- | -------------- | ----------- | ------------------- |
| id                        | CHAR(26)       | PK          |                     |
| user_id                   | CHAR(26)       | NOT NULL，FK |                     |
| platform                  | VARCHAR(16)    | NOT NULL    |                     |
| status                    | VARCHAR(16)    | NOT NULL    | `none`              |
| relation_id               | VARCHAR(64)    | 可空          | 渠道/推广身份，**禁止**下发客户端 |
| authorized_at             | TIMESTAMPTZ(3) | 可空          |                     |
| revoked_at                | TIMESTAMPTZ(3) | 可空          |                     |
| UNIQUE(user_id, platform) |                |             |                     |


对话「授权了哪些平台」读此表。HITL 授权完成回调后 `authorized`。

### 6.12 `ticket` 工单


| 字段                        | 类型             | 约束                   | 注释         |
| ------------------------- | -------------- | -------------------- | ---------- |
| id                        | CHAR(26)       | PK                   | 对外工单号      |
| user_id                   | CHAR(26)       | NOT NULL，FK          | 提交人        |
| conversation_id           | CHAR(26)       | 可空                   | 来源会话       |
| category                  | VARCHAR(32)    | NOT NULL             | `faq_miss` |
| content                   | TEXT           | NOT NULL             | 用户描述       |
| order_id                  | CHAR(26)       | 可空                   | 关联订单       |
| priority                  | VARCHAR(16)    | NOT NULL，默认 `normal` | `normal`   |
| status                    | VARCHAR(16)    | NOT NULL，默认 `open`   | `open`     |
| assignee_sso_sub          | VARCHAR(64)    | 可空                   | 运营处理人      |
| created_at                | TIMESTAMPTZ(3) | NOT NULL             |            |
| updated_at                | TIMESTAMPTZ(3) | NOT NULL             |            |
| INDEX(status, created_at) |                |                      | Admin 列表   |


对话里只建单并回执号，**不假装已修好**。人工回复回写会话为 P1。

`ticket` 只存**当前**状态与优先级。状态变更、优先级变更、用户补充证据、管理员回复、改派 **必须**再插 `ticket_log`，禁止只改工单行。日志只追加，禁止 UPDATE/DELETE 历史行。

`**ticket_log` 处理日志**


| 字段                           | 类型             | 约束                      | 注释                                                                                         |
| ---------------------------- | -------------- | ----------------------- | ------------------------------------------------------------------------------------------ |
| id                           | CHAR(26)       | PK                      |                                                                                            |
| ticket_id                    | CHAR(26)       | NOT NULL，FK → ticket.id | 所属工单                                                                                       |
| kind                         | VARCHAR(32)    | NOT NULL                | `created` | `status_change` | `priority_change` | `evidence` | `reply` | `assignee_change` |
| actor_type                   | VARCHAR(16)    | NOT NULL                | `user` | `admin` | `system`                                                                |
| actor_user_id                | CHAR(26)       | 可空                      | 终端用户操作时（建单、证据）                                                                             |
| actor_sso_sub                | VARCHAR(64)    | 可空                      | 运营操作时                                                                                      |
| from_value                   | VARCHAR(64)    | 可空                      | 变更前：status / priority / assignee_sso_sub                                                   |
| to_value                     | VARCHAR(64)    | 可空                      | 变更后；`created` 时可空                                                                          |
| content                      | TEXT           | 可空                      | 回复正文、证据说明；**禁止**写密钥                                                                        |
| payload                      | JSONB          | NOT NULL，默认 `{}`        | 证据图 `imageObjectKeys[]` 等最小字段                                                              |
| created_at                   | TIMESTAMPTZ(3) | NOT NULL                |                                                                                            |
| INDEX(ticket_id, created_at) |                |                         | 工单时间线                                                                                      |


`create_ticket` 成功时写一条 `kind=created`。用户补充证据：`kind=evidence`（图先走 `POST /api/app/v1/files/image`）。Admin 改状态/优先级/回复/改派：同一事务内更新 `ticket` 并插对应 `kind`。`reply` 不自动当已修好；关单须显式 `status_change` → `closed`。

### 6.13 `wallet` 用户钱包


| 字段               | 类型             | 约束               | 注释      |
| ---------------- | -------------- | ---------------- | ------- |
| user_id          | CHAR(26)       | PK，FK → app_user | 一用户一行   |
| available_amount | NUMERIC(12,2)  | NOT NULL，默认 0    | 可提现     |
| frozen_amount    | NUMERIC(12,2)  | NOT NULL，默认 0    | 提现处理中   |
| total_earned     | NUMERIC(12,2)  | NOT NULL，默认 0    | 累计已入账返佣 |
| updated_at       | TIMESTAMPTZ(3) | NOT NULL         |         |


余额变更必须同时写 `wallet_ledger`，禁止只改钱包行。

`**wallet_ledger` 流水（不可改历史）**


| 字段            | 类型             | 约束          | 注释                             |
| ------------- | -------------- | ----------- | ------------------------------ |
| id            | CHAR(26)       | PK          |                                |
| user_id       | CHAR(26)       | NOT NULL，FK |                                |
| type          | VARCHAR(16)    | NOT NULL    | `commission` \| `withdraw` \| `credit_buy` |
| amount        | NUMERIC(12,2)  | NOT NULL    | 正负以 type 解释                    |
| order_id      | CHAR(26)       | 可空          | 返佣来源 `app_order.id`。`type=commission` 时必填，且该 `order_id` 全局只允许一行 |
| withdrawal_id | CHAR(26)       | 可空          |                                |
| credit_order_id | CHAR(26)     | 可空          | `credit_buy` 时指向积分订单           |
| memo          | VARCHAR(256)   | 可空          | 不对用户展示密钥                       |
| created_at    | TIMESTAMPTZ(3) | NOT NULL    | 「这个月返佣」按本表 `commission` + 时间汇总 |


`type=commission` 的 `amount` 为正，等于该单 `user_commission`，记在**下单用户**名下。部分唯一索引：`UNIQUE (order_id) WHERE type = 'commission' AND order_id IS NOT NULL`。提现、购积分流水的 `order_id` 为空，不受此约束。禁止改历史行。


### 6.14 `withdrawal` 用户提现


| 字段                         | 类型             | 约束          | 注释                                                 |
| -------------------------- | -------------- | ----------- | -------------------------------------------------- |
| id                         | CHAR(26)       | PK          |                                                    |
| user_id                    | CHAR(26)       | NOT NULL，FK |                                                    |
| amount                     | NUMERIC(12,2)  | NOT NULL    | 申请金额，必须为正                                          |
| status                     | VARCHAR(16)    | NOT NULL    | `pending`                                          |
| channel                    | VARCHAR(16)    | NOT NULL    | 如 `alipay`（具体渠道上线前再锁）                              |
| payee_account              | VARCHAR(128)   | NOT NULL    | 收款账号（支付宝账号/手机号/邮箱等）；仅服务端与打款使用，日志与 Agent **禁止**完整回显 |
| account_mask               | VARCHAR(64)    | NOT NULL    | 脱敏展示，由 `payee_account` 生成，如 `138****8000`          |
| reject_reason              | VARCHAR(256)   | 可空          |                                                    |
| created_at                 | TIMESTAMPTZ(3) | NOT NULL    |                                                    |
| updated_at                 | TIMESTAMPTZ(3) | NOT NULL    |                                                    |
| INDEX(user_id, created_at) |                |             |                                                    |


提现申请走 App 已登录接口；对话若要提现，先 HITL 确认再调 Java（P1，P0 可用「我的」页）。Admin 审核。

### 6.15 `app_invite` 邀请关系


| 字段                            | 类型             | 约束                 | 注释          |
| ----------------------------- | -------------- | ------------------ | ----------- |
| id                            | CHAR(26)       | PK                 |             |
| inviter_id                    | CHAR(26)       | NOT NULL，FK        | 上级          |
| invitee_id                    | CHAR(26)       | NOT NULL，UNIQUE，FK | 下级，一人一个邀请人  |
| status                        | VARCHAR(16)    | NOT NULL           | 当前：注册即 `valid`；预留以后改「首次下单」 |
| valid_at                      | TIMESTAMPTZ(3) | 可空                 | 当前：绑定当时刻；以后可改为首次下单时间 |
| created_at                    | TIMESTAMPTZ(3) | NOT NULL           |             |
| INDEX(inviter_id, created_at) |                |                    | 「这个月邀请了多少人」 |


绑定成功即 `status=valid` 且写入 `valid_at`（注册即有效），并在同一事务写 `app_user.inviter_id`。以后若改为「首次下单才有效」，只改判定逻辑，不改表。下级贡献仍按 `wallet_ledger.commission` 汇总（流水在下单用户名下，不在上级名下）。本表不存邀请码明文。`inviter_id` 与 `app_user.inviter_id` 必须相同。

### 6.15.1 `invite_poster_template` 邀请海报模板


| 字段               | 类型             | 约束            | 注释                                           |
| ---------------- | -------------- | ------------- | -------------------------------------------- |
| id               | CHAR(26)       | PK            |                                              |
| name             | VARCHAR(64)    | NOT NULL      | 运营展示名                                        |
| image_object_key | VARCHAR(512)   | NOT NULL      | 模板原图 OSS 键                                   |
| canvas_width     | INT            | NOT NULL      | 模板像素宽                                        |
| canvas_height    | INT            | NOT NULL      | 模板像素高                                        |
| qr_x             | INT            | NOT NULL      | 二维码区左上 X，须 ≥0 且 qr_x+qr_width ≤ canvas_width |
| qr_y             | INT            | NOT NULL      | 左上 Y                                         |
| qr_width         | INT            | NOT NULL      | 贴码区域宽，必须为正                                   |
| qr_height        | INT            | NOT NULL      | 贴码区域高，必须为正                                   |
| sort             | INT            | NOT NULL，默认 0 | 端上左右滑顺序                                      |
| status           | VARCHAR(16)    | NOT NULL      | `active` \| `retired`                         |
| starts_at        | TIMESTAMPTZ(3) | 可空            | 生效起（含）；空=不限下                                 |
| ends_at          | TIMESTAMPTZ(3) | 可空            | 生效止（含）；空=不限上；须晚于 `starts_at`                 |
| created_at       | TIMESTAMPTZ(3) | NOT NULL      |                                              |
| updated_at       | TIMESTAMPTZ(3) | NOT NULL      | 改图/矩形后客户端下次拉列表重贴码                             |

**不建** `invite_poster_render`。贴码只在 APP/H5。二维码内容仅为落地 URL，禁止写入邀请码以外的 PII。模板上的营销文案由运营画在原图里。

### 6.16 `kb_article` 知识库（MCP 接入前）


| 字段         | 类型             | 约束       | 注释             |
| ---------- | -------------- | -------- | -------------- |
| id         | CHAR(26)       | PK       |                |
| title      | VARCHAR(256)   | NOT NULL |                |
| body       | TEXT           | NOT NULL | 运营维护的规则正文      |
| embedding  | vector(1536)   | 可空       | `search_kb` 检索 |
| status     | VARCHAR(16)    | NOT NULL | `active`       |
| updated_at | TIMESTAMPTZ(3) | NOT NULL |                |


后期 MCP 只替换 `search_kb` 实现，本表可停写。

### 6.17 `admin` 身份

管理员**不**落本库用户密码。本地只存审计：

**admin_audit_log**


| 字段         | 类型             | 约束       | 注释                         |
| ---------- | -------------- | -------- | -------------------------- |
| id         | CHAR(26)       | PK       | 审计记录主键                     |
| sso_sub    | VARCHAR(64)    | NOT NULL | omino-sso 的用户 `sub`，本库不存密码 |
| action     | VARCHAR(64)    | NOT NULL | 操作编码，如 `order.sync.retry`  |
| resource   | VARCHAR(128)   | 可空       | 操作对象标识                     |
| detail     | JSONB          | 可空       | 附加上下文，禁止写密钥                |
| created_at | TIMESTAMPTZ(3) | NOT NULL | 操作时间                       |


### 6.18 `credit_account` 对话积分账户

与 §6.13 `wallet` **分户**：本表只服务对话计费。允许钱包余额购买积分（`credit_order.channel=wallet`）。禁止积分提现、禁止积分转回钱包。


| 字段         | 类型             | 约束               | 注释                        |
| ---------- | -------------- | ---------------- | ------------------------- |
| user_id    | CHAR(26)       | PK，FK → app_user | 一用户一行；注册时插入 `available=0` |
| available  | NUMERIC(12,2)  | NOT NULL，默认 0    | 可用积分，两位小数；**禁止为负**        |
| updated_at | TIMESTAMPTZ(3) | NOT NULL         |                           |


余额变更必须同时写 `credit_ledger`。

### 6.19 `credit_ledger` 积分流水（不可改历史）


| 字段                         | 类型             | 约束          | 注释                                           |
| -------------------------- | -------------- | ----------- | -------------------------------------------- |
| id                         | CHAR(26)       | PK          |                                              |
| user_id                    | CHAR(26)       | NOT NULL，FK |                                              |
| type                       | VARCHAR(16)    | NOT NULL    | `purchase` | `consume` | `refund` | `adjust` |
| amount                     | NUMERIC(12,2)  | NOT NULL    | 正数、两位小数；增减由 `type` 解释（`consume` 减少）          |
| balance_after              | NUMERIC(12,2)  | NOT NULL    | 变动后 `available`                              |
| run_id                     | CHAR(26)       | 可空          | 对话扣费                                         |
| credit_order_id            | CHAR(26)       | 可空          | 购套餐                                          |
| billed_tokens              | INT            | 可空          | 本次对应的 billed token                           |
| memo                       | VARCHAR(256)   | 可空          | 不对用户展示密钥                                     |
| created_at                 | TIMESTAMPTZ(3) | NOT NULL    |                                              |
| INDEX(user_id, created_at) |                |             |                                              |


### 6.20 Token 与积分比例（Nacos，无表）

**不建** `credit_rate` 表。`credit.tokens-per-credit`（默认 `1000`，必须 ≥1）放 Nacos，热更新，改比例 **不**追溯已入账流水。公式见 [`conversation.md`](./conversation.md) §4.1.3。Admin `GET /api/admin/v1/credit-rate` 只读当前值。

### 6.21 `credit_package` 积分套餐


| 字段         | 类型             | 约束            | 注释                   |
| ---------- | -------------- | ------------- | -------------------- |
| id         | CHAR(26)       | PK            |                      |
| name       | VARCHAR(64)    | NOT NULL      | 如「入门包」               |
| credits    | NUMERIC(12,2)  | NOT NULL      | 购买到账积分，必须为正，两位小数     |
| price      | NUMERIC(12,2)  | NOT NULL      | 售价（元），必须为正           |
| sort       | INT            | NOT NULL，默认 0 | 卡片展示序                |
| status     | VARCHAR(16)    | NOT NULL      | `active` | `retired` |
| created_at | TIMESTAMPTZ(3) | NOT NULL      |                      |
| updated_at | TIMESTAMPTZ(3) | NOT NULL      |                      |


已有 `credit_order` 引用的套餐禁止物理删除，只 `retired`。

### 6.22 `credit_order` 积分支付订单

与电商 `app_order` 分离。


| 字段                               | 类型             | 约束          | 注释                                                     |
| -------------------------------- | -------------- | ----------- | ------------------------------------------------------ |
| id                               | CHAR(26)       | PK          |                                                        |
| user_id                          | CHAR(26)       | NOT NULL，FK |                                                        |
| package_id                       | CHAR(26)       | NOT NULL，FK | 下单时套餐快照仍以下列 credits/price 为准                           |
| credits                          | NUMERIC(12,2)  | NOT NULL    | 下单时套餐积分快照，两位小数                                         |
| pay_amount                       | NUMERIC(12,2)  | NOT NULL    | 下单时售价快照（元）                                             |
| channel                          | VARCHAR(16)    | NOT NULL    | `wechat_pay` \| `alipay` \| `wallet`（返佣可用余额） |
| status                           | VARCHAR(16)    | NOT NULL    | `pending` | `paid` | `expired` | `failed` | `refunded` |
| client_order_id                  | VARCHAR(64)    | NOT NULL    | 客户端幂等键                                                 |
| pay_txn_id                       | VARCHAR(128)   | 可空          | 渠道交易号                                                  |
| paid_at                          | TIMESTAMPTZ(3) | 可空          |                                                        |
| created_at                       | TIMESTAMPTZ(3) | NOT NULL    |                                                        |
| updated_at                       | TIMESTAMPTZ(3) | NOT NULL    |                                                        |
| UNIQUE(user_id, client_order_id) |                |             |                                                        |
| INDEX(user_id, created_at)       |                |             |                                                        |


支付成功才加积分。`channel=wallet`：同一事务扣 `wallet.available`（流水 `credit_buy`）并加积分（`purchase`）；钱包不够返回 `40300`。退款：先扣积分，钱包渠道则退回钱包；积分不够则拒绝退款。**禁止**积分转回钱包以外的路径、禁止用积分提现。Demo/H5 不做 Apple IAP。

### 6.23 `llm_usage` 对话 Token 明细

每次对 LLM 的一次完成（含流式结束后的官方 usage）一行。Langfuse 仍记 trace，**账单以本表 + `credit_ledger` 为准**。


| 字段                         | 类型             | 约束            | 注释                                |
| -------------------------- | -------------- | ------------- | --------------------------------- |
| id                         | CHAR(26)       | PK            |                                   |
| user_id                    | CHAR(26)       | NOT NULL，FK   |                                   |
| conversation_id            | CHAR(26)       | NOT NULL      |                                   |
| run_id                     | CHAR(26)       | NOT NULL      |                                   |
| llm_model_id               | CHAR(26)       | 可空，FK → llm_model.id | 本轮命中的模型配置。`local` 走环境变量兜底时为空 |
| model                      | VARCHAR(64)    | NOT NULL      | 厂商模型名，如 `deepseek-chat`；与 `llm_model.model_name` 一致 |
| prompt_tokens              | INT            | NOT NULL，默认 0 |                                   |
| completion_tokens          | INT            | NOT NULL，默认 0 |                                   |
| total_tokens               | INT            | NOT NULL      | 须等于前两列之和（以厂商为准时以厂商 `total` 写入并校验） |
| credits                    | NUMERIC(12,2)  | NOT NULL      | 本行扣费，两位小数                         |
| created_at                 | TIMESTAMPTZ(3) | NOT NULL      |                                   |
| INDEX(user_id, created_at) |                |               |                                   |
| INDEX(run_id)              |                |               |                                   |


禁止把 prompt 原文写入本表。

### 6.24 `app_release` 版本记录（渠道包）


| 字段 | 类型 | 约束 | 注释 |
| --- | --- | --- | --- |
| id | CHAR(26) | PK | |
| client_type | VARCHAR(8) | NOT NULL | `android` \| `ios` \| `h5` |
| channel | VARCHAR(32) | NOT NULL | Android 渠道包；iOS `appstore`；H5 `web` |
| version_code | INT | NOT NULL | `yyyyMMdd` 八位整数，如 `20261029`；比较用整数大小 |
| version_name | VARCHAR(32) | NOT NULL | 展示，如 `1.2.0` |
| force_update | TINYINT | NOT NULL，默认 0 | `1` 强制 |
| title | VARCHAR(128) | NOT NULL | 更新弹窗标题 |
| changelog | TEXT | NOT NULL | 更新说明 |
| download_url | VARCHAR(1024) | 可空 | APK / 商店；H5 可空 |
| status | VARCHAR(16) | NOT NULL | `draft` \| `published` |
| published_at | TIMESTAMPTZ(3) | 可空 | |
| created_at | TIMESTAMPTZ(3) | NOT NULL | |
| updated_at | TIMESTAMPTZ(3) | NOT NULL | |
| UNIQUE(client_type, channel, version_code) | | | 同端同渠道同一天一条；当天改包则 PATCH 该行 |

`version_code` 合法范围 `20000101`–`20991231` 且必须是真实日历日（禁止 `20261301`）。`version_code_min/max`（配置、公告）同一格式。


### 6.25 `app_runtime_config` 特殊配置


| 字段 | 类型 | 约束 | 注释 |
| --- | --- | --- | --- |
| id | CHAR(26) | PK | |
| name | VARCHAR(64) | NOT NULL | 运营备注 |
| client_type | VARCHAR(8) | NOT NULL | `android` \| `ios` \| `h5` \| `*` |
| channel | VARCHAR(32) | NOT NULL | 具体渠道或 `*` |
| version_code_min | INT | 可空 | 含；`yyyyMMdd`；空=不限下 |
| version_code_max | INT | 可空 | 含；`yyyyMMdd`；空=不限上 |
| payload | JSONB | NOT NULL，默认 `{}` | 键见 [`api-app.md`](./api-app.md) §4.8 |
| priority | INT | NOT NULL，默认 0 | 大者覆盖 |
| status | VARCHAR(16) | NOT NULL | `active` \| `retired` |
| updated_at | TIMESTAMPTZ(3) | NOT NULL | |


匹配算法见 [`api-app.md`](./api-app.md) §4.8。禁止用本表存密钥。

### 6.26 `announcement` 公告


| 字段 | 类型 | 约束 | 注释 |
| --- | --- | --- | --- |
| id | CHAR(26) | PK | |
| title | VARCHAR(128) | NOT NULL | |
| body | TEXT | NOT NULL | 展示文案；禁止密钥 |
| client_types | JSONB | NOT NULL，默认 `[]` | `[]`=全端；否则 `android`/`ios`/`h5` 子集 |
| channels | JSONB | NOT NULL，默认 `[]` | `[]`=全渠道 |
| version_code_min | INT | 可空 | `yyyyMMdd` |
| version_code_max | INT | 可空 | `yyyyMMdd` |
| sort | INT | NOT NULL，默认 0 | |
| status | VARCHAR(16) | NOT NULL | `draft` \| `published` \| `retired` |
| starts_at | TIMESTAMPTZ(3) | 可空 | |
| ends_at | TIMESTAMPTZ(3) | 可空 | |
| created_at | TIMESTAMPTZ(3) | NOT NULL | |
| updated_at | TIMESTAMPTZ(3) | NOT NULL | |


### 6.27 `inbox_message` 站内私信


| 字段 | 类型 | 约束 | 注释 |
| --- | --- | --- | --- |
| id | CHAR(26) | PK | |
| user_id | CHAR(26) | NOT NULL，FK | 只此一人 |
| title | VARCHAR(128) | NOT NULL | |
| body | TEXT | NOT NULL | |
| read_at | TIMESTAMPTZ(3) | 可空 | 空=未读 |
| created_at | TIMESTAMPTZ(3) | NOT NULL | |
| INDEX(user_id, created_at) | | | |


禁止群发同一接口刷全表；要群发用公告。运营发送写 `admin_audit_log`。

### 6.28 `chat_suggestion` 新对话推荐问题

空对话页上的问题，全局一份，不按用户个性化。点击后把 `text` 当作本轮用户输入发送（既有 `POST /chat/turns`），不另开意图。


| 字段 | 类型 | 约束 | 注释 |
| --- | --- | --- | --- |
| id | CHAR(26) | PK | |
| text | VARCHAR(128) | NOT NULL | 展示文案，也是发出去的用户文本。去首尾空白后 1–128 字 |
| sort | INT | NOT NULL，默认 0 | 小者在前；相同再按 `id` |
| status | VARCHAR(16) | NOT NULL | `active` \| `retired` |
| created_at | TIMESTAMPTZ(3) | NOT NULL | |
| updated_at | TIMESTAMPTZ(3) | NOT NULL | |


同时处于 `active` 的最多 **12** 条。超出则创建或重新上架返回 `40001`。消费者只拉 `active`。

### 6.29 `agent_prompt` Agent 提示词

图上会调用大模型的位置，各有一条固定 `key`。正文以本表为准。Langfuse 只做 trace，**不**再当提示词数据源，避免两处各改各的。

运营只能改 `content`。不能新增、删除或改名 `key`；新位置先改本表枚举，再 Flyway 插行。`content` 去空白后不得为空。


| key | 谁用 |
| --- | --- |
| `intent.router` | 意图、槽位、置信度 |
| `clarify` | 置信不够或槽位缺失时的追问 |
| `reply.shop` | 导购回复 |
| `reply.account` | 账户 / 授权 / 统计回复 |
| `reply.order` | 订单回复 |
| `reply.earn` | 邀请与收益回复 |
| `reply.support` | 知识库 / 工单回复 |
| `reply.chitchat` | 闲聊 |

列：

| 字段 | 类型 | 约束 | 注释 |
| --- | --- | --- | --- |
| key | VARCHAR(64) | PK | 上表之一，不可改 |
| name | VARCHAR(64) | NOT NULL | 后台展示名 |
| content | TEXT | NOT NULL | 提示词正文。禁止写入用户隐私与密钥 |
| updated_at | TIMESTAMPTZ(3) | NOT NULL | |
| updated_by | VARCHAR(64) | 可空 | 最后修改的 SSO `sub` |


Worker 每次开 run 读完全表，放进 `graph/invoke` 的 `prompts`。缺任一 key，或正文为空：不调 Agent，本 run 以 Agent 失败结束。禁止在仓库里再放一套会覆盖本表的默认提示词（Flyway 种子只负责首行，之后以库为准）。

`slot_fill` 与 `coref` 是代码节点，不单列提示词。

### 6.30 `llm_model` 大模型与权重分流

多条 OpenAI 兼容配置（DeepSeek 以及同协议的其它模型）。不引入第二套 LLM SDK。密钥只在 Java 加密保存；Agent 不读本表，只使用当次 invoke 注入的一条。


| 字段 | 类型 | 约束 | 注释 |
| --- | --- | --- | --- |
| id | CHAR(26) | PK | |
| name | VARCHAR(64) | NOT NULL，UNIQUE | 运营看到的名字 |
| base_url | VARCHAR(256) | NOT NULL | 如 `https://api.deepseek.com`，无尾斜杠 |
| model_name | VARCHAR(64) | NOT NULL | 请求体里的 `model` |
| api_key_enc | BYTEA | NOT NULL | AES-GCM 密文。禁止明文列。密钥材料为环境变量 `LLM_CONFIG_KEY` |
| weight | INT | NOT NULL，默认 0，≥0 | 分流权重。`0` 表示不参与 |
| enabled | TINYINT | NOT NULL，默认 0 | `1` 启用 `0` 停用 |
| timeout_ms | INT | NOT NULL，默认 60000 | 单次模型调用超时，1000–120000 |
| created_at | TIMESTAMPTZ(3) | NOT NULL | |
| updated_at | TIMESTAMPTZ(3) | NOT NULL | |


**分流**（每个 run 一次，不是每次模型调用一次）：候选 = `enabled=1` 且 `weight>0`。概率 = 该行 `weight / 候选权重之和`。命中后，这一轮里所有 LLM 调用都用这一条，`llm_usage.model` 写 `model_name`，`llm_usage.llm_model_id` 写本行 id。这样同一轮的扣费和 trace 对得上同一个模型。

没有候选：`test` / `prod` 不调 Agent，run 失败（`50020`）。仅 `APP_ENV=local` 且本表没有任何候选时，允许不注入模型，由 Agent 使用进程环境变量 `OPENAI_API_BASE` / `OPENAI_API_KEY` / `OPENAI_MODEL`。一旦存在候选，环境变量不再参与分流。

读接口不返回密钥，只返回是否已设置，以及末 4 位掩码。更新时省略密钥字段 = 保留原密钥。日志、审计 `detail`、Langfuse 禁止出现密钥明文。改权重、启停、换地址、换密钥写 `admin_audit_log`（`action=llm_model.update`，`detail` 只有字段名与权重前后值）。

---
