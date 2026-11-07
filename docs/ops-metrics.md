# 运营指标

本文是运营指标的唯一权威：口径、汇总表、只读接口、后台页面、实现步骤都写在这里。`data-model.md`、`api-admin.md`、`admin-ui.md` 只留链接，不要把字段再抄一份。

业务表结构、订单状态、分成和入账规则仍以 [`data-model.md`](./data-model.md) 为准。本文不改那些规则。

读者是接下来实现这块的人。按文末步骤做完，应能在管理后台看到三页数，且对话、转链、同步订单、扣积分、提现的请求路径不跑这些聚合 SQL。

---

## 1. 这门生意在算什么

本产品不卖货。用户在对话里选品，转链后到淘宝、京东、拼多多、唯品会下单。公司收入是两截：

1. 媒体返佣留下的部分。`user_commission = round(media_commission × commission.user-share-rate, 2)`，默认比例 `0.80`。平台留存 = `media_commission − user_commission`。用户返佣进下单用户的钱包。上级 `parent_user_id` 现阶段不分成。
2. 用户用微信或支付宝购买的对话积分实收。用返佣余额买积分是负债换形态，不是新现金。积分不能提现，不能转回钱包；只有该笔积分订单退款时，钱包渠道才退回余额。

实付 GMV（`pay_amount`）表示联盟侧规模，放在漏斗里，不放在收入第一行。

阶段北极星：**注册后 7 日内产生过至少一笔已归因订单的用户数**（`app_order.user_id` 非空，按下单用户去重）。有量之后，周报主指标改为：

已结算平台留存 + 微信/支付宝积分实收 − 模型成本 − 已打款提现。

钱包余额和未消耗积分是负债，不是利润。

---

## 2. 口径

时间桶用上海日历日：`(timestamptz AT TIME ZONE 'Asia/Shanghai')::date`。金额在库里用 `NUMERIC`，接口按 `AGENTS.md` §5 输出两位小数字符串。计数输出整数。

### 2.1 每天先看的 8 个数

工作台第一屏只放这 8 个，外加现有三个待办（同步失败平台数、工单行数、待审提现数）。待办继续走现有 `GET /api/admin/v1/dashboard`，因为它们是三张小表上的计数。下面 8 个只读汇总表。

| 展示名 | metric_key | 口径 |
| --- | --- | --- |
| 新增注册 | `signup_count` | 当日 `app_user.created_at` 落入该日、且 `deleted_at` 为空的人数 |
| 首转链用户 | `first_click_users` | 当日第一次出现 `click_event` 的用户数。该用户历史上更早的点击不算 |
| 新归因订单 | `order_attributed_count` | 当日 `app_order.created_at` 落入该日、且 `user_id` 非空的订单数 |
| 归因率 | `order_attr_rate` | 当日新写入订单中 `user_id` 非空的比例。分母为 0 时接口返回 `null`，页面显示「—」 |
| 已结算平台留存 | `platform_retain_settled` | 当日 `settled_at` 落入该日的订单，`media_commission − user_commission` 之和。两列有空则该单不计入 |
| 钱包负债 | `wallet_liability` | 快照：`sum(available_amount + frozen_amount)`。不是按日累加 |
| 积分耗尽次数 | `credit_exhausted_count` | 当日 `stats_run_outcome.terminal = credit_exhausted` 的行数。原因另见 `credit_exhausted_start`、`credit_exhausted_mid` |
| 同步异常平台数 | `sync_error_platforms` | 快照：`order_sync_cursor.last_error` 非空的平台数，只计 `taobao` `jd` `pdd` `vip` |

每个日指标附带前一日的值和差额，供卡片上的涨跌。快照类比较上一轮汇总成功时的值。

### 2.2 三本账

**联盟佣金（利润）**

| metric_key | 口径 |
| --- | --- |
| `media_commission_open` | `settle_status = pending` 的 `media_commission` 之和。预计，尚未入账 |
| `user_commission_open` | 同上，`user_commission` |
| `platform_retain_open` | 上两列之差 |
| `media_commission_settled` | `settle_status = settled` 且 `settled_at` 在所选区间的媒体返佣 |
| `user_commission_settled` | 同上，用户返佣 |
| `platform_retain_settled` | 已结算留存 |
| `media_unattributed` | `user_id` 为空且 `media_commission > 0` 的媒体返佣之和。快照，不按日 |

分成比例只影响未结算行。汇总任务不改订单。页面用只读方式显示当前 Nacos `commission.user-share-rate`；改比例仍走配置中心，本页不提供写入。

**用户钱包（负债）**

| metric_key | 口径 |
| --- | --- |
| `wallet_available` | `sum(available_amount)` |
| `wallet_frozen` | `sum(frozen_amount)` |
| `wallet_liability` | 两者之和 |
| `wallet_earned` | `sum(total_earned)` |
| `ledger_commission` | `wallet_ledger.type = commission` 的 `amount` 之和 |
| `ledger_withdraw` | `type = withdraw` 的 `amount` 之和 |
| `ledger_credit_buy` | `type = credit_buy` 的 `amount` 之和 |
| `wallet_reconcile_diff` | `ledger_commission − ledger_withdraw − ledger_credit_buy − wallet_liability` |

对账差的绝对值大于 `0.01` 时，三本账页标成不平，并写入风险名单。流水金额为正，方向由 `type` 决定，与 [`data-model.md`](./data-model.md) §6.13 一致。申请和拒绝提现不写流水，所以不能把 `withdrawal` 的待审金额算进这条等式。

**对话积分**

| metric_key | 口径 |
| --- | --- |
| `credit_cash_paid` | `credit_order.status = paid` 且 `channel` 为 `wechat_pay` 或 `alipay`，`paid_at` 在区间内的 `pay_amount` |
| `credit_wallet_paid` | 同上，`channel = wallet`。页面注明这不是新现金 |
| `credit_liability` | `sum(credit_account.available)` |
| `credit_purchase` | `credit_ledger.type = purchase` 的 `amount` 之和 |
| `credit_consume` | `type = consume` |
| `credit_refund` | `type = refund` |
| `credit_adjust` | `type = adjust` |
| `llm_cost_yuan` | `llm_usage` 按 `model` 乘 `stats_model_price`。无标价的模型不按 0 算，接口另给 `unpricedModels[]` |

近似经营结果由接口算好返回，前端不做公式：

`operating_result = platform_retain_settled + credit_cash_paid − llm_cost_yuan − ledger_withdraw`

模型有未标价时，`operating_result` 为 `null`，页面写「有模型未标价，经营结果先空着」。

### 2.3 用户

| metric_key | 口径 |
| --- | --- |
| `signup_count` | 见 §2.1。可按 `stats_user_origin` 拆 `client_type`、`channel`。没有来源行的用户，维度值记 `unknown` |
| `signup_invited_count` | 当日注册且 `inviter_id` 非空 |
| `message_users` | 当日至少一条 `conversation_message.role = user` 的用户数 |
| `user_messages` | 当日用户消息条数 |
| `intent_messages` | 当日带 `intent` 的消息数，维度 `intent` 为点分名 |
| `first_shop_users` | 用户第一条 `intent` 以 `shop.` 开头的消息落在当日 |
| `first_click_users` | 见 §2.1 |
| `first_order_users_d1` | 注册当日或次日出现首笔已归因订单的用户数，记在注册日 |
| `first_order_users_d7` | 注册后 7 个上海日内（含注册日）出现首笔已归因订单的用户数，记在注册日 |
| `activated_users_d7` | 与北极星相同，等于 `first_order_users_d7` |
| `auth_ok_users` | 快照：`platform_authorization.status = authorized` 的用户数，按平台。京东不放进「必须授权」漏斗，单独一格 |

留存：`retention_d1`、`retention_d7`、`retention_d30`。分母是注册日的 `signup_count`。分子是这些用户在注册日后第 1 / 7 / 30 个上海日仍有用户消息的人数。注册不满对应天数时，该格为 `null`。

邀请质量按周算，记在周一日：被邀请用户与 `inviter_id` 为空的用户，各自的 7 日首转链率、7 日首单率。维度 `dim_kind = cohort`，`dim_value` 为 `invited` 或 `organic`。

澄清率、闲聊占比：只汇总已经写在 `conversation_message.intent` 上的值。`chitchat` 条数 / 有意图的条数即闲聊占比。消息里没有单独的澄清标记时，不做澄清率，也不为这个数去改 Agent。

### 2.4 订单漏斗

每个平台一条，步骤从左到右。所选区间是上海日闭区间。

| 步骤 | metric_key | 口径 |
| --- | --- | --- |
| 出卡 | `funnel_card` | `stats_event.name = card_impression`。没有埋点数据时该步为 `null`，页面写「尚未采集」 |
| 打开详情 | `funnel_detail` | `user_event.kind = browse` |
| 转链成功 | `funnel_click` | `click_event` 条数 |
| 同步进库 | `funnel_order` | `app_order` 条数，按 `created_at` |
| 已归因 | `funnel_attributed` | 上一步且 `user_id` 非空 |
| 离开待支付 | `funnel_paid` | `order_status` 属于 `pending_ship`、`shipped`、`completed` |
| 已入账 | `funnel_settled` | `settle_status = settled` 且 `settled_at` 在区间内 |

另外按平台给出：`gmv`（`pay_amount` 之和）、`commission_rate`（`media_commission / pay_amount`，分母为 0 则 `null`）、`cancel_rate`、`unknown_count`（`order_status = unknown`）、`settle_days_avg`（`settled_at − ordered_at` 的平均天数，没有 `ordered_at` 的单不参与）。

转链到下单不在第一期做 24 小时窗口匹配。第一期用上面的分平台计数漏斗。窗口归因需要商品级连接，放到后面，不要在第一期的请求路径里做。

### 2.5 对话与供给

| metric_key | 口径 |
| --- | --- |
| `run_terminal` | `stats_run_outcome` 按 `terminal` 计数：`done` `error` `cancelled` `credit_exhausted` |
| `llm_tokens` | `llm_usage.total_tokens` 之和，可按 `model` 拆 |
| `llm_credits` | `llm_usage.credits` 之和 |
| `ticket_open` | 快照：`ticket.status = open` |
| `ticket_unassigned` | 快照：`open` 且 `assignee_sso_sub` 为空 |
| `ticket_created` | 当日新建工单，可按 `category` 拆 |

上游失败 `50010`、Agent 失败 `50020` 目前只出现在访问日志和 SSE `error`，没有落业务表。第一期用 `run_terminal = error` 代表对话失败，不解析访问日志。

### 2.6 风险名单

每轮汇总清空 `stats_risk_item` 再写入当前仍成立的行。阈值写在任务类的常量里，改阈值要发版。

| kind | 条件 |
| --- | --- |
| `unattributed_commission` | `user_id` 为空且 `media_commission > 0`。`ref_id` 为订单 id |
| `settle_overdue` | `settle_status = pending` 且 `ordered_at` 早于现在 30 天 |
| `settled_then_cancelled` | `settle_status = settled` 且 `order_status = cancelled` |
| `withdrawal_pending_old` | `withdrawal.status = pending` 且 `created_at` 早于现在 48 小时 |
| `wallet_reconcile` | 对账差绝对值 > 0.01。`ref_id` 固定 `wallet` |
| `credit_adjust_blank` | `credit_ledger.type = adjust` 且 `memo` 为空或空白。只扫最近 30 天 |
| `invite_burst` | 同一 `inviter_id` 在最近 24 小时邀请 ≥ 20 人，且这些下级的 `click_event` 为 0 |

已结算后平台把佣金改小：结算后列冻结，同步不会改历史，库里看不出改口。第一期只做 `settled_then_cancelled`。不要为了改口检测去改订单同步事务。

---

## 3. 方案

约束：汇总不能拖慢对话、转链、同步和支付；业务代码少改；后台要能直接看懂。

| 方案 | 为什么不选 |
| --- | --- |
| 打开工作台时直接扫订单、流水、消息 | 页面和业务抢同一连接池，数据一大就会把后台和接口一起拖慢 |
| 在每个业务 Service 里加计数器 | 改动面散，事务里多写一条还可能把主流程卷进失败 |
| 另起 ClickHouse 或大数据仓 | 当前阶段多一套库，运维和口径都会分叉 |
| 物化视图挂在业务连接上刷新 | 刷新时的 IO 和锁在主库上，和订单同步撞车 |

采用的做法：

- 业务库继续当源头。汇总任务跑在 **Admin 进程**，用单独的小连接池，只做 SQL 汇总，写入 `stats_*`。
- 工作台、漏斗、三本账、风险名单的 HTTP **只读 `stats_*`**。
- 业务表上已有的事实不再写第二遍。`click_event`、`user_event`（`browse`）、`conversation_message`、`app_order`、`wallet_ledger`、`credit_ledger`、`llm_usage` 都直接扫。
- 只在三处旁路补现在没有的事实：注册来源、run 终态、少量客户端行为。旁路失败只打日志，主流程照常返回。
- 消费者 H5 页面还没做。埋点接口先留好，等页面落地时再调用。第一期图表不依赖埋点也能看订单、资金和对话。

延迟：任务每 10 分钟跑完一轮。页面顶部写「数据截至 {last_success_at}」。这是给运营看的数，允许落后一个周期。

---

## 4. 运行隔离

任务只放在 `shop-api-admin`。不要挂到 APP 的订单同步上，也不要在 `shop-api-app` 里开调度。

Admin 新增一个数据源，与后台普通请求的池分开：

- Bean 名 `statsJdbc`，类型 `JdbcTemplate`。
- 连接池最大 2。
- 连接初始化执行 `SET statement_timeout = '20s'` 和 `SET lock_timeout = '3s'`。
- 地址、账号与 Admin 主库相同，不新建数据库，不要求只读账号。任务自己保证只对业务表 `SELECT`，只对 `stats_*` 写入。

多副本：每轮先 `SELECT pg_try_advisory_lock(81234001)`。拿不到锁就本轮退出，不算失败。结束时 `pg_advisory_unlock(81234001)`。锁常量就用这个数。

单轮里每一步独立事务。某一步失败，把错误写入 `stats_rollup_cursor.last_error`，继续下一步。下一步使用的源数据仍是业务表，不读上一步的半成品。

订单状态和佣金会在同步时改写，所以订单类指标每次重算最近 90 个上海日，按 `created_at`、`ordered_at`、`settled_at`、`updated_from_platform_at` 四列里任一落入窗口的行取并集。流水、消息、点击按 `created_at` 重算最近 3 个上海日，用来补迟到写入。快照（钱包合计、积分余额、游标、授权人数）每次全表聚合，这些表是一用户一行或四行平台，量小。

禁止在任务里调用商品、转链、Agent、支付、联盟 SDK。

---

## 5. 表

Flyway 新文件：`services/java/shop-api-common/src/main/resources/db/migration/V3__stats.sql`。不建数据库外键。不给这些表建 Entity、Mapper。Admin 与 App 的旁路都用 `JdbcTemplate`。

```sql
CREATE TABLE stats_daily_metric (
    day         DATE           NOT NULL,
    metric_key  VARCHAR(64)    NOT NULL,
    dim_kind    VARCHAR(16)    NOT NULL DEFAULT '',
    dim_value   VARCHAR(64)    NOT NULL DEFAULT '',
    value       NUMERIC(20,4)  NOT NULL,
    updated_at  TIMESTAMPTZ(3) NOT NULL,
    PRIMARY KEY (day, metric_key, dim_kind, dim_value)
);

CREATE TABLE stats_snapshot (
    metric_key  VARCHAR(64)    PRIMARY KEY,
    value       NUMERIC(20,4)  NOT NULL,
    updated_at  TIMESTAMPTZ(3) NOT NULL
);

CREATE TABLE stats_risk_item (
    id           CHAR(26)       PRIMARY KEY,
    kind         VARCHAR(32)    NOT NULL,
    ref_id       VARCHAR(64)    NOT NULL,
    title        VARCHAR(256)   NOT NULL,
    amount       NUMERIC(12,2),
    detected_at  TIMESTAMPTZ(3) NOT NULL,
    detail       JSONB          NOT NULL DEFAULT '{}',
    CONSTRAINT stats_risk_item_uk UNIQUE (kind, ref_id)
);

CREATE TABLE stats_rollup_cursor (
    job_name         VARCHAR(64)    PRIMARY KEY,
    last_started_at  TIMESTAMPTZ(3),
    last_success_at  TIMESTAMPTZ(3),
    last_error       VARCHAR(512),
    rows_written     INT            NOT NULL DEFAULT 0
);

CREATE TABLE stats_user_origin (
    user_id       CHAR(26)       PRIMARY KEY,
    client_type   VARCHAR(8)     NOT NULL,
    channel       VARCHAR(32)    NOT NULL,
    version_code  INT,
    created_at    TIMESTAMPTZ(3) NOT NULL
);

CREATE TABLE stats_run_outcome (
    run_id      CHAR(26)       PRIMARY KEY,
    user_id     CHAR(26),
    terminal    VARCHAR(32)    NOT NULL,
    reason      VARCHAR(32),
    created_at  TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT stats_run_outcome_terminal_ck CHECK (terminal IN ('done', 'error', 'cancelled', 'credit_exhausted'))
);
CREATE INDEX idx_stats_run_outcome_created ON stats_run_outcome (created_at);

CREATE TABLE stats_event (
    id           CHAR(26)       PRIMARY KEY,
    user_id      CHAR(26),
    name         VARCHAR(32)    NOT NULL,
    client_type  VARCHAR(8),
    channel      VARCHAR(32),
    platform     VARCHAR(16),
    props        JSONB          NOT NULL DEFAULT '{}',
    created_at   TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT stats_event_name_ck CHECK (name IN ('card_impression', 'poster_share', 'land_view', 'land_open_app'))
);
CREATE INDEX idx_stats_event_name_created ON stats_event (name, created_at);

CREATE TABLE stats_model_price (
    model_name                      VARCHAR(64)    PRIMARY KEY,
    prompt_yuan_per_million         NUMERIC(12,6)  NOT NULL,
    completion_yuan_per_million     NUMERIC(12,6)  NOT NULL,
    updated_at                      TIMESTAMPTZ(3) NOT NULL,
    updated_by                      VARCHAR(64)
);
```

`dim_kind` 只允许空串、`platform`、`client_type`、`channel`、`intent`、`model`、`terminal`、`category`、`cohort`。空串表示该指标的合计。`dim_value` 空串表示合计。不要把多个维度乘在同一行里。

同一主键用 `INSERT ... ON CONFLICT DO UPDATE` 覆盖 `value` 与 `updated_at`。重算窗口外的旧行留着，任务不删除历史日。

为了让 20 秒内能扫完，在同一迁移里给业务表补索引。当前数据量小，直接 `CREATE INDEX`。以后表变大，再单独做不在事务里的并发建索引。

```sql
CREATE INDEX idx_app_user_created ON app_user (created_at);
CREATE INDEX idx_app_order_created ON app_order (created_at);
CREATE INDEX idx_app_order_ordered ON app_order (ordered_at);
CREATE INDEX idx_app_order_settled ON app_order (settled_at);
CREATE INDEX idx_app_order_updated ON app_order (updated_from_platform_at);
CREATE INDEX idx_click_event_created ON click_event (created_at);
CREATE INDEX idx_click_event_user_created ON click_event (user_id, created_at);
CREATE INDEX idx_message_created ON conversation_message (created_at);
CREATE INDEX idx_wallet_ledger_type_created ON wallet_ledger (type, created_at);
CREATE INDEX idx_credit_ledger_type_created ON credit_ledger (type, created_at);
CREATE INDEX idx_credit_order_paid ON credit_order (paid_at);
CREATE INDEX idx_llm_usage_created ON llm_usage (created_at);
```

`stats_model_price` 是运营录入的单价，单位是元 / 百万 token。它不放进 `llm_model`，避免和分流、密钥写在一起。

---

## 6. 业务代码只允许碰的三处

这三处都在主流程成功之后，用 try/catch 包住。异常只记日志，不往外抛，不占业务事务。

### 6.1 注册来源

`stats_user_origin` 记每个用户第一次登录时的端和渠道。登录接口今天不强制客户端头（见 [`app-api-catalog.md`](./app-api-catalog.md) 客户端头一节）。头不齐就跳过，登录照常成功。不要为了指标把缺头改成 `40001`。

调用点：APP 登录成功、已经拿到 `userId`、响应返回之前。SQL：

```sql
INSERT INTO stats_user_origin (user_id, client_type, channel, version_code, created_at)
VALUES (?, ?, ?, ?, ?)
ON CONFLICT (user_id) DO NOTHING
```

`client_type` 只接受 `android` `ios` `h5`，`channel` 最长 32。不合法就跳过。

### 6.2 对话终态

`credit_exhausted` 今天只在 Redis Stream 里，终态后大约 30 分钟过期，汇总任务读不到。在 `ChatEventServiceImpl.append` 里，Redis 写入已经成功、且 `event` 属于 `done`、`error`、`cancelled`、`credit_exhausted` 时，再写 `stats_run_outcome`。

- `user_id` 用现有的 `owner(runId)`。没有就写空。
- `reason` 只在 `credit_exhausted` 时从 `data` JSON 取 `reason`，只接受 `INSUFFICIENT_START` 和 `EXHAUSTED_MID`。解析失败则 `reason` 为空，终态行仍然写入。
- `ON CONFLICT (run_id) DO NOTHING`。
- Redis 写入失败时不要写这行。

### 6.3 客户端行为

`POST /api/app/v1/telemetry/events`，要登录，要客户端头。body：

```json
{ "events": [ { "name": "card_impression", "platform": "jd", "props": { "goodsId": "g_jd_1" } } ] }
```

- `events` 最多 20 条。
- `name` 只允许 `card_impression`、`poster_share`。
- 控制器把合法事件放进内存队列后立刻返回 `{ "accepted": n }`。队列容量 2000，满了就丢掉这批并打一条日志，HTTP 仍返回成功，`accepted` 为 0。
- 一个后台线程每秒或满 100 条批量插入 `stats_event`。
- 同一用户每分钟最多接受 60 条，用 Redis `INCR` + 60 秒过期。Redis 不可用时仍接受，靠 body 上限兜住。

落地页没有登录。另开 `POST /api/app/v1/telemetry/land`，不要 JWT。body 只有 `name`（`land_view` 或 `land_open_app`）和 `inviteCode`（最长 8）。按 IP 每分钟 30 次。这个接口等 `apps/land` 真正有页面时再接，第一期可以只把接口和测试做完。

H5、Android、iOS 的页面还没到埋点那一步。第一期不改消费者页面，不造假页面。

不要在 Agent、Tool、订单同步、钱包入账、积分扣费里加统计写入。

---

## 7. 汇总任务

类：`com.omino.shopai.admin.stats.StatsRollupJob`。一个类里按顺序调用私有方法，不要拆成一串只被这一处使用的小类。

`@Scheduled(fixedDelay = 600_000, initialDelay = 60_000)`。Admin 启动类打开调度。APP 不打开这个任务。

`stats_rollup_cursor.job_name` 固定 `rollup`。开始时写 `last_started_at` 并清空 `last_error`。全部步骤走完写 `last_success_at` 和 `rows_written`。任一步失败则 `last_error` 保留最后一条，成功时间不更新。

步骤：

1. 快照：钱包四项、对账差、积分余额、同步异常平台数、授权人数、待办工单。写入 `stats_snapshot`。
2. 风险名单：按 §2.6 重建 `stats_risk_item`。
3. 近 3 日：注册、消息、意图、点击、积分订单、积分流水、token、run 终态、工单新建。写入 `stats_daily_metric`。
4. 近 90 日订单：归因、GMV、佣金、漏斗后四步、结算天数、取消与未知。出卡与详情：详情扫 `user_event`，出卡扫 `stats_event`，同样限制在 90 日。
5. 留存与 7 日激活：只重算「今天往前 31 天」这些注册日，因为更早的 D30 已经定格。
6. 模型成本：近 3 日 `llm_usage` 乘 `stats_model_price`。无价模型不写入 `llm_cost_yuan` 的合计，把模型名留在任务日志即可；查询接口自己再算 `unpricedModels`。

日指标 SQL 的形状（以新增注册为例，其余指标按 §2 的表写进同一个方法）：

```sql
INSERT INTO stats_daily_metric (day, metric_key, dim_kind, dim_value, value, updated_at)
SELECT (created_at AT TIME ZONE 'Asia/Shanghai')::date,
       'signup_count', '', '', COUNT(*)::numeric, now()
FROM app_user
WHERE deleted_at IS NULL
  AND created_at >= ?
  AND created_at < ?
GROUP BY 1
ON CONFLICT (day, metric_key, dim_kind, dim_value)
DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at
```

按渠道拆时 `dim_kind = 'channel'`，来源表左连接 `stats_user_origin`，没有行则 `dim_value = 'unknown'`。

订单重算用 `WHERE created_at >= ? OR ordered_at >= ? OR settled_at >= ? OR updated_from_platform_at >= ?`，参数是窗口起点。分组键用各自口径里的那个时间，不要四列混成一个日。

---

## 8. 管理端接口

都在已有 Admin 会话下。未登录行为与其它 `/api/admin/v1/**` 相同。只读，除了模型单价。Controller：`AdminStatsController`，`@Tag` 与每个方法的 `@Operation` 按 `AGENTS.md` 写。`summary` 写做什么，不写路径。返回 `ApiResponse.ok`。

金额字段是字符串两位小数。计数是 JSON 整数。比例是字符串，四位小数，例如 `"0.3810"`。`null` 表示还不能算。

### 8.1 `GET /api/admin/v1/stats/overview?day=YYYY-MM-DD`

`day` 缺省为上海今天。

```json
{
  "asOf": "2026-11-01T01:20:00+08:00",
  "stale": false,
  "kpis": [
    {
      "key": "signup_count",
      "label": "新增注册",
      "value": "12",
      "previous": "9",
      "delta": "3",
      "unit": "count"
    }
  ],
  "trend": [
    { "day": "2026-10-26", "signup_count": "1", "first_click_users": "0", "order_attributed_count": "0", "platform_retain_settled": "0.00" }
  ]
}
```

`kpis` 顺序固定为 §2.1 的 8 个。`trend` 是该日往前共 7 天，缺日补 0。`asOf` 取 `stats_rollup_cursor.last_success_at`。从未成功时 `asOf` 为 `null`，`stale` 为 `true`，`kpis` 为空数组。成功时间早于现在 30 分钟也把 `stale` 设为 `true`。

归因率的 `unit` 为 `rate`。留存和负债的 `unit` 为 `cny`。其余为 `count`。

### 8.2 `GET /api/admin/v1/stats/funnel?from=YYYY-MM-DD&to=YYYY-MM-DD`

缺省最近 7 天，含首尾。跨度最长 90 天，超出返回 `40001`。

```json
{
  "asOf": "2026-11-01T01:20:00+08:00",
  "platforms": [
    {
      "platform": "jd",
      "steps": [
        { "key": "funnel_card", "label": "出卡", "value": null },
        { "key": "funnel_detail", "label": "打开详情", "value": "28" },
        { "key": "funnel_click", "label": "转链成功", "value": "40" },
        { "key": "funnel_order", "label": "同步进库", "value": "11" },
        { "key": "funnel_attributed", "label": "已归因", "value": "9" },
        { "key": "funnel_paid", "label": "离开待支付", "value": "7" },
        { "key": "funnel_settled", "label": "已入账", "value": "2" }
      ],
      "gmv": "320.50",
      "commissionRate": "0.0850",
      "cancelRate": "0.0909",
      "unknownCount": "0",
      "settleDaysAvg": "12.5"
    }
  ]
}
```

平台顺序固定 `taobao`、`jd`、`pdd`、`vip`。某平台没有数据也要返回，数值为 `"0"`。`funnel_card` 在没有任何 `card_impression` 时，该步 `value` 为 `null`。有零条真实事件（任务跑过、值是 0）则返回 `"0"`。区分办法：汇总任务在跑完出卡步骤后，往 `stats_snapshot` 写 `card_impression_ready = 1`。没有这个快照时接口返回 `null`。

### 8.3 `GET /api/admin/v1/stats/ledger?from=&to=`

缺省本月 1 日到今天。

```json
{
  "asOf": "2026-11-01T01:20:00+08:00",
  "shareRate": "0.80",
  "commission": {
    "openMedia": "10.00",
    "openUser": "8.00",
    "openRetain": "2.00",
    "settledMedia": "100.00",
    "settledUser": "80.00",
    "settledRetain": "20.00",
    "unattributedMedia": "3.50"
  },
  "wallet": {
    "available": "70.00",
    "frozen": "10.00",
    "liability": "80.00",
    "earned": "80.00",
    "commission": "80.00",
    "withdraw": "0.00",
    "creditBuy": "0.00",
    "reconcileDiff": "0.00",
    "balanced": true
  },
  "credit": {
    "cashPaid": "30.00",
    "walletPaid": "0.00",
    "liability": "12.50",
    "purchase": "20.00",
    "consume": "7.50",
    "refund": "0.00",
    "adjust": "0.00",
    "llmCost": "1.20",
    "unpricedModels": []
  },
  "operatingResult": "48.80"
}
```

`shareRate` 读 Admin 进程已有的分成配置。读不到时为 `null`，页面显示「比例未加载」。`balanced` 在对账差绝对值 ≤ 0.01 时为 `true`。

### 8.4 `GET /api/admin/v1/stats/risk`

```json
{
  "asOf": "2026-11-01T01:20:00+08:00",
  "list": [
    {
      "kind": "settle_overdue",
      "refId": "01J...",
      "title": "待结算超过 30 天",
      "amount": "1.20",
      "detectedAt": "2026-11-01T01:20:00+08:00"
    }
  ]
}
```

`title` 用固定中文，不要把用户手机、收款账号、联盟 PID 放进 `title` 或 `detail`。订单类 `refId` 给前端做成指向 `/orders/{id}` 的链接。提现类指向 `/withdrawals`。

### 8.5 模型单价

| 方法 | 路径 | 行为 |
| --- | --- | --- |
| GET | `/api/admin/v1/stats/model-prices` | 列出 `stats_model_price`，并带上近 7 日 `llm_usage` 里出现过、但还没有单价的 `model` |
| PUT | `/api/admin/v1/stats/model-prices/{modelName}` | body：`promptYuanPerMillion`、`completionYuanPerMillion`，都是正数字符串。写入 `updated_by` 为当前 SSO `sub`。记一条已有样式的管理审计，`action = stats_model_price.update`，`detail` 只有模型名和前后单价 |

单价不进 Nacos。

现有 `GET /api/admin/v1/dashboard` 保持三个待办字段，不要把 8 个指标塞进去。

---

## 9. 页面

改 `apps/admin-web`。壳、登录、菜单折叠保持现状。新页放在左栏「工作台」下面，仍用 `@element-plus/icons-vue`：

| 菜单 | 路由名 | 路径 | 图标 |
| --- | --- | --- | --- |
| 工作台 | `dashboard` | `/` | 已有 `Odometer` |
| 漏斗 | `statsFunnel` | `/stats/funnel` | `Histogram` |
| 三本账 | `statsLedger` | `/stats/ledger` | `Wallet` |
| 风险 | `statsRisk` | `/stats/risk` | `Warning` |

工作台继续拉 `/v1/dashboard` 的三个待办，并拉 `/v1/stats/overview`。

### 9.1 视觉

这是运营每天看的屏，数字要静、对齐、少颜色。

- 页边距 20px。区块之间 16px。
- 卡片白底，1px `var(--el-border-color-lighter)`，圆角 12px，不要重阴影。
- 主数字 28px、字重 600、`font-variant-numeric: tabular-nums`、颜色 `#1f2329`。
- 标签 13px、`#86909c`。
- 涨跌 12px。大于 0 用 `#00b42a`，小于 0 用 `#f53f3f`，为 0 用 `#86909c`。比例和金额的涨跌同样用颜色，不要再加一套红绿底。
- 图表高度 280px。只引入折线和柱状，颜色顺序 `#165dff`、`#14c9c9`、`#f7ba1e`、`#722ed1`。关闭 toolbox。动画 400ms。
- 顶部一条细信息，不用错误样式的红条：数据截至时间。`stale = true` 时用 `el-alert` 的 `warning`，文案「汇总落后，先不要拿这屏做结算」。从未跑过时用 `el-empty`，文案「汇总还没有跑完」。
- 金额右对齐。表格用 Element Plus，密度与现有订单表一致。

### 9.2 工作台 `/`

1. 截至时间。
2. 八个指标，CSS grid，桌面四列、窄屏两列。每张卡：标签、主数字、较前一日的差额。
3. 三个待办小卡：同步失败、工单、待审提现。数字来自原 dashboard 接口。点击分别去 `/orders/sync`、`/tickets`、`/withdrawals`。
4. 一张 7 日折线，四条线：新增注册、首转链用户、新归因订单、已结算平台留存。留存用右轴，因为单位是元。

### 9.3 漏斗 `/stats/funnel`

顶上日期范围，默认近 7 天，最长 90 天。

四个平台并排。每个平台一张卡：上面是步骤横向条形图，条的长度用该平台内部的最大值归一，条内写绝对数；条下写相对上一步的转化率（上一步为 0 或 `null` 则显示「—」）。卡片底部一行：GMV、佣金率、取消率、未知状态单数、平均结算天数。

出卡为 `null` 时，该条位置留空并写「尚未采集」，不要画成 0。

### 9.4 三本账 `/stats/ledger`

三列，列标题就是「联盟佣金」「用户钱包」「对话积分」。

- 联盟列：未结算媒体 / 用户 / 留存，已结算媒体 / 用户 / 留存，未归因媒体返佣，当前分成比例。
- 钱包列：可用、冻结、负债、累计入账，以及「佣金流水 − 打款 − 余额购积分」的展开。差额为 0 时列头一个绿色「平」；否则红色「不平」并带差额。
- 积分列：现金实收、余额购积分（旁边注明不是新现金）、积分余额、购入、消耗、退款、调账、模型成本。未标价模型用 `el-tag` 列出来，并链到单价抽屉。
- 页脚一条大数字：经营结果。为 `null` 时显示「有模型未标价」。

单价用抽屉，不新开路由。字段两个：百万 prompt 单价、百万 completion 单价。保存走 PUT。列表里出现过的模型名可以点进来补价。

### 9.5 风险 `/stats/risk`

一张表：类型中文名、说明、金额、发现时间、跳转。没有行时用空状态「这轮没有命中风险」。类型中文：

| kind | 中文 |
| --- | --- |
| `unattributed_commission` | 有佣金但没归因 |
| `settle_overdue` | 待结算超过 30 天 |
| `settled_then_cancelled` | 已入账后又取消 |
| `withdrawal_pending_old` | 提现超过 48 小时未完成 |
| `wallet_reconcile` | 钱包对不上 |
| `credit_adjust_blank` | 积分调账没有备注 |
| `invite_burst` | 邀请很多但没人转链 |

### 9.6 前端文件

| 文件 | 职责 |
| --- | --- |
| `src/stats/chart.ts` | 只注册 CanvasRenderer、LineChart、BarChart、Grid、Tooltip、Legend |
| `src/stats/format.ts` | 按 `unit` 显示整数、两位金额、百分比 |
| `src/stats/api.ts` | 四个 GET 和一个 PUT |
| `src/views/dashboard.vue` | 改成 §9.2，保留待办 |
| `src/views/stats-funnel.vue` | 漏斗 |
| `src/views/stats-ledger.vue` | 三本账和单价抽屉 |
| `src/views/stats-risk.vue` | 风险表 |
| `src/layouts/AdminShell.vue` | 三个菜单项 |
| `src/router/index.ts` | 三条路由 |

组件就放在这几个页面里。四个页面共用的只有 `chart.ts`、`format.ts`、`api.ts`。不要再做一套通用 BI 组件库。

---

## 10. 要引入的包

实现的第一步是改 `AGENTS.md` §2.4，写上精确版本，然后再改 `apps/admin-web/package.json`。只用这些，不引入第二套图表库，不引入图表主题包。

| 包 | 版本 | 许可 | 用什么 | 不用什么 |
| --- | --- | --- | --- | --- |
| `echarts` | `6.1.0` | Apache-2.0 | 折线、柱状、网格、提示框、图例。按需 `echarts/core` 引入 | 地图、关系图、3D、词云、完整 `echarts` 入口 |
| `vue-echarts` | `8.3.2` | MIT | `VChart` 把上面的实例挂进 Vue 3 | 它的图形编辑器和多余渲染器 |

这两个版本是 2026 年仍在维护的发布：ECharts `6.1.0`（2026-05-19），vue-echarts `8.3.2`（2026-10-09），peer 要求 Vue `^3.3` 与 ECharts `^6`。仓库里的 Vue 是 `3.5.13`，对得上。

Java 不新增运行时依赖。汇总用 Spring 已有的 `JdbcTemplate` 和 `@Scheduled`。

测试若仓库里还没有 PostgreSQL 测试容器，再按 `AGENTS.md` §7.1.6 检索 `org.testcontainers:junit-jupiter` 与 `postgresql` 的当前维护版本，写入 §2.1，范围只限 `shop-api-admin` 的 test。许可用 Apache-2.0。不进可运行 jar。

---

## 11. 实现步骤

一步做完再做下一步。每步都有可以验收的结果。

1. **锁版本**。`AGENTS.md` §2.4 加上 §10 的两行。安装依赖。不改 Java 父 POM。
2. **迁移**。提交 `V3__stats.sql`，内容就是 §5。本地已有库上 Flyway 能跑过。不要改 `V1`、`V2`。
3. **Admin 数据源**。`shop-api-admin` 增加 `statsJdbc`，池大小和超时按 §4。在 `application-local.yml` 里只复用已有的 PG 地址环境变量，不新写一套口令。
4. **汇总任务**。实现 `StatsRollupJob` 的六步和咨询锁。用夹具数据跑一轮：插入一个用户、一条已结算订单、一笔佣金流水，断言 `platform_retain_settled`、`wallet_reconcile_diff` 和风险行。对账故意改错一分钱时，`balanced` 为 false。
5. **只读接口**。四个 GET 与单价 PUT。Controller 不写 SQL。查询只打 `stats_*` 和分成配置。接口测试覆盖：没跑过汇总时 overview 的 `stale` 与空 `kpis`；漏斗跨度 91 天返回 `40001`。
6. **页面**。按 §9 做四屏。菜单在收起状态下仍能悬停看到「漏斗」「三本账」「风险」。浏览器里走完：打开工作台、改日期看漏斗、看三本账对账标记、打开风险空态、保存一个模型单价后再看成本是否不再是未标价。
7. **两处旁路**。`ChatEventServiceImpl.append` 与登录成功处按 §6.1、§6.2 接入。测试：Redis 终态写入成功后多一行 `stats_run_outcome`；旁路抛异常时登录和 SSE 仍成功。
8. **埋点接口**。§6.3 的两个 POST 与队列。单测队列满时请求仍 200。第一期不改 H5 / 落地页 / 原生页面。
9. **文档指针**。确认 `data-model.md`、`api-admin.md`、`admin-ui.md` 仍然只链接到本文，没有第二套字段。

不要做的事：

- 不改 Agent，不改联盟适配器，不改订单入账事务。
- 不把汇总 SQL 放进现有订单、用户、钱包 Controller。
- 不做导出 Excel、不做自定义报表设计器、不做二级分销看板。
- 不把 GMV 放到工作台第一行。

---

## 12. 验收

- Admin 进程停掉汇总或拿到锁失败时，APP 的对话、转链、下单同步不受影响。
- 工作台 HTTP 的 SQL 只出现 `stats_daily_metric`、`stats_snapshot`、`stats_rollup_cursor`，以及原 dashboard 的三张小表。
- 八个指标的定义与 §2.1 一致，页面上能看出截至时间。
- 钱包等式不平时，三本账出现「不平」，风险名单有 `wallet_reconcile`。
- 模型没标价时经营结果为空，并列出模型名。
- 旁路写入失败不会让登录或一轮对话失败。
