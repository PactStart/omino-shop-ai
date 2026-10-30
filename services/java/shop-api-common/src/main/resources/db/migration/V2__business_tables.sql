-- 全部业务表。不建 LangGraph checkpoint。只向前迁移，没有 down。
-- 不建数据库外键。关联由应用事务校验。

CREATE TABLE app_user (
    id           CHAR(26)       PRIMARY KEY,
    nickname     VARCHAR(64)    NOT NULL DEFAULT '用户',
    avatar_url   VARCHAR(512),
    phone        VARCHAR(20),
    status       SMALLINT       NOT NULL DEFAULT 1,
    locale       VARCHAR(16)    NOT NULL DEFAULT 'zh-CN',
    invite_code  CHAR(8)        NOT NULL,
    inviter_id   CHAR(26),
    push_enabled SMALLINT       NOT NULL DEFAULT 1,
    created_at   TIMESTAMPTZ(3) NOT NULL,
    updated_at   TIMESTAMPTZ(3) NOT NULL,
    deleted_at   TIMESTAMPTZ(3),
    CONSTRAINT app_user_phone_uk UNIQUE (phone),
    CONSTRAINT app_user_invite_code_uk UNIQUE (invite_code),
    CONSTRAINT app_user_no_self_invite CHECK (inviter_id IS NULL OR inviter_id <> id)
);

CREATE TABLE app_user_identity (
    id            CHAR(26)       PRIMARY KEY,
    user_id       CHAR(26)       NOT NULL,
    provider      VARCHAR(16)    NOT NULL,
    provider_uid  VARCHAR(128)   NOT NULL,
    union_id      VARCHAR(128),
    created_at    TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT app_user_identity_provider_uk UNIQUE (provider, provider_uid)
);

CREATE TABLE app_user_preference (
    id                   CHAR(26)       PRIMARY KEY,
    user_id              CHAR(26)       NOT NULL UNIQUE,
    preferred_platforms  JSONB          NOT NULL DEFAULT '[]',
    budget_max           NUMERIC(12,2),
    disliked_keywords    JSONB          NOT NULL DEFAULT '[]',
    extra                JSONB          NOT NULL DEFAULT '{}',
    updated_at           TIMESTAMPTZ(3) NOT NULL
);

CREATE TABLE app_user_installed_app (
    id           CHAR(26)       PRIMARY KEY,
    user_id      CHAR(26)       NOT NULL,
    platform     VARCHAR(16)    NOT NULL,
    installed    SMALLINT       NOT NULL,
    reported_at  TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT app_user_installed_app_uk UNIQUE (user_id, platform),
    CONSTRAINT app_user_installed_app_platform_ck CHECK (platform IN ('taobao', 'jd', 'pdd', 'vip'))
);

CREATE TABLE conversation (
    id          CHAR(26)       PRIMARY KEY,
    user_id     CHAR(26)       NOT NULL,
    title       VARCHAR(128),
    last_intent VARCHAR(64),
    created_at  TIMESTAMPTZ(3) NOT NULL,
    updated_at  TIMESTAMPTZ(3) NOT NULL
);

CREATE TABLE conversation_message (
    id               CHAR(26)       PRIMARY KEY,
    conversation_id  CHAR(26)       NOT NULL,
    client_msg_id    VARCHAR(64)    NOT NULL,
    role             VARCHAR(16)    NOT NULL,
    content          TEXT           NOT NULL,
    intent           VARCHAR(64),
    payload          JSONB,
    created_at       TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT conversation_message_client_uk UNIQUE (conversation_id, client_msg_id)
);

CREATE TABLE user_event (
    id                 CHAR(26)       PRIMARY KEY,
    user_id            CHAR(26)       NOT NULL,
    kind               VARCHAR(32)    NOT NULL,
    platform           VARCHAR(16),
    goods_id           CHAR(26),
    platform_goods_id  VARCHAR(64),
    order_id           CHAR(26),
    title              VARCHAR(512),
    payload            JSONB          NOT NULL DEFAULT '{}',
    created_at         TIMESTAMPTZ(3) NOT NULL
);
CREATE INDEX idx_user_event_user_kind_created ON user_event (user_id, kind, created_at);

CREATE TABLE memory_semantic (
    id          CHAR(26)       PRIMARY KEY,
    user_id     CHAR(26)       NOT NULL,
    kind        VARCHAR(32)    NOT NULL,
    text        TEXT           NOT NULL,
    embedding   vector(1536),
    confidence  NUMERIC(3,2)   NOT NULL,
    source      VARCHAR(16)    NOT NULL,
    valid_to    TIMESTAMPTZ(3),
    created_at  TIMESTAMPTZ(3) NOT NULL
);
CREATE INDEX idx_memory_semantic_embedding ON memory_semantic USING hnsw (embedding vector_cosine_ops);

CREATE TABLE goods_snapshot (
    id                     CHAR(26)        PRIMARY KEY,
    platform               VARCHAR(16)     NOT NULL,
    platform_goods_id      VARCHAR(64)     NOT NULL,
    platform_goods_alias   VARCHAR(64),
    title                  VARCHAR(512)    NOT NULL,
    image_url              VARCHAR(1024),
    price                  NUMERIC(12,2),
    coupon_price           NUMERIC(12,2),
    shop_name              VARCHAR(256),
    sales_count            BIGINT,
    review_count           INT,
    goods_url              VARCHAR(1024),
    raw_hash               CHAR(64),
    updated_at             TIMESTAMPTZ(3)  NOT NULL,
    CONSTRAINT goods_snapshot_platform_uk UNIQUE (platform, platform_goods_id),
    CONSTRAINT goods_snapshot_platform_ck CHECK (platform IN ('taobao', 'jd', 'pdd', 'vip'))
);

CREATE TABLE goods_collection (
    id               CHAR(26)       PRIMARY KEY,
    user_id          CHAR(26)       NOT NULL,
    conversation_id  CHAR(26),
    run_id           CHAR(26),
    title            VARCHAR(128)   NOT NULL,
    source           VARCHAR(16)    NOT NULL,
    created_at       TIMESTAMPTZ(3) NOT NULL
);
CREATE INDEX idx_goods_collection_user_created ON goods_collection (user_id, created_at);

CREATE TABLE goods_collection_item (
    id             CHAR(26)       PRIMARY KEY,
    collection_id  CHAR(26)       NOT NULL,
    goods_id       CHAR(26)       NOT NULL,
    sort           INT            NOT NULL DEFAULT 0,
    removed_at     TIMESTAMPTZ(3),
    created_at     TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT goods_collection_item_uk UNIQUE (collection_id, goods_id)
);

CREATE TABLE goods_favorite (
    id          CHAR(26)       PRIMARY KEY,
    user_id     CHAR(26)       NOT NULL,
    goods_id    CHAR(26)       NOT NULL,
    created_at  TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT goods_favorite_uk UNIQUE (user_id, goods_id)
);

CREATE TABLE order_sync_cursor (
    platform                VARCHAR(16)    PRIMARY KEY,
    last_sync_create_time   TIMESTAMPTZ(3),
    last_sync_update_time   TIMESTAMPTZ(3),
    last_error              VARCHAR(512),
    updated_at              TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT order_sync_cursor_platform_ck CHECK (platform IN ('taobao', 'jd', 'pdd', 'vip'))
);

CREATE TABLE click_event (
    id                 CHAR(26)       PRIMARY KEY,
    user_id            CHAR(26)       NOT NULL,
    platform           VARCHAR(16)    NOT NULL,
    goods_id           CHAR(26),
    platform_goods_id  VARCHAR(64)    NOT NULL,
    created_at         TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT click_event_platform_ck CHECK (platform IN ('taobao', 'jd', 'pdd', 'vip'))
);

CREATE TABLE platform_authorization (
    id             CHAR(26)       PRIMARY KEY,
    user_id        CHAR(26)       NOT NULL,
    platform       VARCHAR(16)    NOT NULL,
    status         VARCHAR(16)    NOT NULL,
    relation_id    VARCHAR(64),
    authorized_at  TIMESTAMPTZ(3),
    revoked_at     TIMESTAMPTZ(3),
    CONSTRAINT platform_authorization_uk UNIQUE (user_id, platform),
    CONSTRAINT platform_authorization_platform_ck CHECK (platform IN ('taobao', 'jd', 'pdd', 'vip'))
);

CREATE TABLE ticket (
    id                  CHAR(26)       PRIMARY KEY,
    user_id             CHAR(26)       NOT NULL,
    conversation_id     CHAR(26),
    category            VARCHAR(32)    NOT NULL,
    content             TEXT           NOT NULL,
    order_id            CHAR(26),
    priority            VARCHAR(16)    NOT NULL DEFAULT 'normal',
    status              VARCHAR(16)    NOT NULL DEFAULT 'open',
    assignee_sso_sub    VARCHAR(64),
    created_at          TIMESTAMPTZ(3) NOT NULL,
    updated_at          TIMESTAMPTZ(3) NOT NULL
);
CREATE INDEX idx_ticket_status_created ON ticket (status, created_at);

CREATE TABLE ticket_log (
    id              CHAR(26)       PRIMARY KEY,
    ticket_id       CHAR(26)       NOT NULL,
    kind            VARCHAR(32)    NOT NULL,
    actor_type      VARCHAR(16)    NOT NULL,
    actor_user_id   CHAR(26),
    actor_sso_sub   VARCHAR(64),
    from_value      VARCHAR(64),
    to_value        VARCHAR(64),
    content         TEXT,
    payload         JSONB          NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ(3) NOT NULL
);
CREATE INDEX idx_ticket_log_ticket_created ON ticket_log (ticket_id, created_at);

CREATE TABLE wallet (
    user_id           CHAR(26)       PRIMARY KEY,
    available_amount  NUMERIC(12,2)  NOT NULL DEFAULT 0,
    frozen_amount     NUMERIC(12,2)  NOT NULL DEFAULT 0,
    total_earned      NUMERIC(12,2)  NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT wallet_available_nonneg CHECK (available_amount >= 0),
    CONSTRAINT wallet_frozen_nonneg CHECK (frozen_amount >= 0),
    CONSTRAINT wallet_earned_nonneg CHECK (total_earned >= 0)
);

CREATE TABLE wallet_ledger (
    id                CHAR(26)       PRIMARY KEY,
    user_id           CHAR(26)       NOT NULL,
    type              VARCHAR(16)    NOT NULL,
    amount            NUMERIC(12,2)  NOT NULL,
    order_id          CHAR(26),
    withdrawal_id     CHAR(26),
    credit_order_id   CHAR(26),
    memo              VARCHAR(256),
    created_at        TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT wallet_ledger_type_ck CHECK (type IN ('commission', 'withdraw', 'credit_buy'))
);
CREATE UNIQUE INDEX uk_wallet_ledger_commission_order
    ON wallet_ledger (order_id)
    WHERE type = 'commission' AND order_id IS NOT NULL;

CREATE TABLE withdrawal (
    id                     CHAR(26)       PRIMARY KEY,
    user_id                CHAR(26)       NOT NULL,
    amount                 NUMERIC(12,2)  NOT NULL,
    status                 VARCHAR(16)    NOT NULL,
    channel                VARCHAR(16)    NOT NULL,
    payee_account          VARCHAR(128)   NOT NULL,
    account_mask           VARCHAR(64)    NOT NULL,
    reject_reason          VARCHAR(256),
    provider_transfer_id   VARCHAR(64),
    paid_at                TIMESTAMPTZ(3),
    created_at             TIMESTAMPTZ(3) NOT NULL,
    updated_at             TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT withdrawal_amount_pos CHECK (amount > 0),
    CONSTRAINT withdrawal_status_ck CHECK (status IN ('pending', 'paid', 'rejected')),
    CONSTRAINT withdrawal_channel_ck CHECK (channel = 'alipay')
);
CREATE INDEX idx_withdrawal_user_created ON withdrawal (user_id, created_at);

CREATE TABLE app_order (
    id                          CHAR(26)        PRIMARY KEY,
    user_id                     CHAR(26),
    platform                    VARCHAR(16)     NOT NULL,
    platform_order_id           VARCHAR(64)     NOT NULL,
    goods_id                    CHAR(26),
    goods_title                 VARCHAR(512)    NOT NULL,
    goods_image_url             VARCHAR(1024),
    pay_amount                  NUMERIC(12,2),
    parent_user_id              CHAR(26),
    media_commission            NUMERIC(12,2),
    user_commission             NUMERIC(12,2),
    click_id                    CHAR(26),
    order_status                VARCHAR(32)     NOT NULL,
    settle_status               VARCHAR(16)     NOT NULL,
    wallet_ledger_id            CHAR(26)        UNIQUE,
    ordered_at                  TIMESTAMPTZ(3),
    shipped_at                  TIMESTAMPTZ(3),
    settled_at                  TIMESTAMPTZ(3),
    updated_from_platform_at    TIMESTAMPTZ(3),
    created_at                  TIMESTAMPTZ(3)  NOT NULL,
    CONSTRAINT app_order_platform_uk UNIQUE (platform, platform_order_id),
    CONSTRAINT app_order_platform_ck CHECK (platform IN ('taobao', 'jd', 'pdd', 'vip')),
    CONSTRAINT app_order_status_ck CHECK (order_status IN ('pending_pay', 'pending_ship', 'shipped', 'completed', 'cancelled', 'unknown')),
    CONSTRAINT app_order_settle_ck CHECK (settle_status IN ('none', 'pending', 'settled'))
);


CREATE TABLE app_invite (
    id           CHAR(26)       PRIMARY KEY,
    inviter_id   CHAR(26)       NOT NULL,
    invitee_id   CHAR(26)       NOT NULL UNIQUE,
    status       VARCHAR(16)    NOT NULL,
    valid_at     TIMESTAMPTZ(3),
    created_at   TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT app_invite_no_self CHECK (inviter_id <> invitee_id)
);
CREATE INDEX idx_app_invite_inviter_created ON app_invite (inviter_id, created_at);

CREATE TABLE invite_poster_template (
    id                 CHAR(26)       PRIMARY KEY,
    name               VARCHAR(64)    NOT NULL,
    image_object_key   VARCHAR(512)   NOT NULL,
    canvas_width       INT            NOT NULL,
    canvas_height      INT            NOT NULL,
    qr_x               INT            NOT NULL,
    qr_y               INT            NOT NULL,
    qr_width           INT            NOT NULL,
    qr_height          INT            NOT NULL,
    sort               INT            NOT NULL DEFAULT 0,
    status             VARCHAR(16)    NOT NULL,
    starts_at          TIMESTAMPTZ(3),
    ends_at            TIMESTAMPTZ(3),
    created_at         TIMESTAMPTZ(3) NOT NULL,
    updated_at         TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT invite_poster_status_ck CHECK (status IN ('active', 'retired')),
    CONSTRAINT invite_poster_qr_pos CHECK (qr_x >= 0 AND qr_y >= 0 AND qr_width > 0 AND qr_height > 0),
    CONSTRAINT invite_poster_qr_box CHECK (qr_x + qr_width <= canvas_width AND qr_y + qr_height <= canvas_height),
    CONSTRAINT invite_poster_window CHECK (starts_at IS NULL OR ends_at IS NULL OR ends_at > starts_at)
);

CREATE TABLE kb_article (
    id          CHAR(26)       PRIMARY KEY,
    title       VARCHAR(256)   NOT NULL,
    body        TEXT           NOT NULL,
    embedding   vector(1536),
    status      VARCHAR(16)    NOT NULL,
    updated_at  TIMESTAMPTZ(3) NOT NULL
);
CREATE INDEX idx_kb_article_embedding ON kb_article USING hnsw (embedding vector_cosine_ops);

CREATE TABLE admin_audit_log (
    id          CHAR(26)       PRIMARY KEY,
    sso_sub     VARCHAR(64)    NOT NULL,
    action      VARCHAR(64)    NOT NULL,
    resource    VARCHAR(128),
    detail      JSONB,
    created_at  TIMESTAMPTZ(3) NOT NULL
);

CREATE TABLE credit_account (
    user_id     CHAR(26)       PRIMARY KEY,
    available   NUMERIC(12,2)  NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT credit_account_nonneg CHECK (available >= 0)
);

CREATE TABLE credit_package (
    id          CHAR(26)       PRIMARY KEY,
    name        VARCHAR(64)    NOT NULL,
    credits     NUMERIC(12,2)  NOT NULL,
    price       NUMERIC(12,2)  NOT NULL,
    sort        INT            NOT NULL DEFAULT 0,
    status      VARCHAR(16)    NOT NULL,
    created_at  TIMESTAMPTZ(3) NOT NULL,
    updated_at  TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT credit_package_credits_pos CHECK (credits > 0),
    CONSTRAINT credit_package_price_pos CHECK (price > 0),
    CONSTRAINT credit_package_status_ck CHECK (status IN ('active', 'retired'))
);

CREATE TABLE credit_order (
    id                CHAR(26)       PRIMARY KEY,
    user_id           CHAR(26)       NOT NULL,
    package_id        CHAR(26)       NOT NULL,
    credits           NUMERIC(12,2)  NOT NULL,
    pay_amount        NUMERIC(12,2)  NOT NULL,
    channel           VARCHAR(16)    NOT NULL,
    status            VARCHAR(16)    NOT NULL,
    client_order_id   VARCHAR(64)    NOT NULL,
    pay_txn_id        VARCHAR(128),
    paid_at           TIMESTAMPTZ(3),
    created_at        TIMESTAMPTZ(3) NOT NULL,
    updated_at        TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT credit_order_client_uk UNIQUE (user_id, client_order_id),
    CONSTRAINT credit_order_channel_ck CHECK (channel IN ('wechat_pay', 'alipay', 'wallet')),
    CONSTRAINT credit_order_status_ck CHECK (status IN ('pending', 'paid', 'expired', 'failed', 'refunded'))
);
CREATE INDEX idx_credit_order_user_created ON credit_order (user_id, created_at);

CREATE TABLE credit_ledger (
    id                CHAR(26)       PRIMARY KEY,
    user_id           CHAR(26)       NOT NULL,
    type              VARCHAR(16)    NOT NULL,
    amount            NUMERIC(12,2)  NOT NULL,
    balance_after     NUMERIC(12,2)  NOT NULL,
    run_id            CHAR(26),
    credit_order_id   CHAR(26),
    billed_tokens     INT,
    memo              VARCHAR(256),
    created_at        TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT credit_ledger_type_ck CHECK (type IN ('purchase', 'consume', 'refund', 'adjust')),
    CONSTRAINT credit_ledger_amount_pos CHECK (amount > 0),
    CONSTRAINT credit_ledger_balance_nonneg CHECK (balance_after >= 0)
);
CREATE INDEX idx_credit_ledger_user_created ON credit_ledger (user_id, created_at);


CREATE TABLE llm_model (
    id           CHAR(26)       PRIMARY KEY,
    name         VARCHAR(64)    NOT NULL UNIQUE,
    base_url     VARCHAR(256)   NOT NULL,
    model_name   VARCHAR(64)    NOT NULL,
    api_key_enc  BYTEA          NOT NULL,
    weight       INT            NOT NULL DEFAULT 0,
    enabled      SMALLINT       NOT NULL DEFAULT 0,
    timeout_ms   INT            NOT NULL DEFAULT 60000,
    created_at   TIMESTAMPTZ(3) NOT NULL,
    updated_at   TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT llm_model_weight_nonneg CHECK (weight >= 0),
    CONSTRAINT llm_model_timeout_ck CHECK (timeout_ms BETWEEN 1000 AND 120000)
);

CREATE TABLE llm_usage (
    id                  CHAR(26)       PRIMARY KEY,
    user_id             CHAR(26)       NOT NULL,
    conversation_id     CHAR(26)       NOT NULL,
    run_id              CHAR(26)       NOT NULL,
    llm_model_id        CHAR(26),
    model               VARCHAR(64)    NOT NULL,
    prompt_tokens       INT            NOT NULL DEFAULT 0,
    completion_tokens   INT            NOT NULL DEFAULT 0,
    total_tokens        INT            NOT NULL,
    credits             NUMERIC(12,2)  NOT NULL,
    created_at          TIMESTAMPTZ(3) NOT NULL
);
CREATE INDEX idx_llm_usage_user_created ON llm_usage (user_id, created_at);
CREATE INDEX idx_llm_usage_run ON llm_usage (run_id);

CREATE TABLE app_release (
    id             CHAR(26)       PRIMARY KEY,
    client_type    VARCHAR(8)     NOT NULL,
    channel        VARCHAR(32)    NOT NULL,
    version_code   INT            NOT NULL,
    version_name   VARCHAR(32)    NOT NULL,
    force_update   SMALLINT       NOT NULL DEFAULT 0,
    title          VARCHAR(128)   NOT NULL,
    changelog      TEXT           NOT NULL,
    download_url   VARCHAR(1024),
    status         VARCHAR(16)    NOT NULL,
    published_at   TIMESTAMPTZ(3),
    created_at     TIMESTAMPTZ(3) NOT NULL,
    updated_at     TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT app_release_version_uk UNIQUE (client_type, channel, version_code),
    CONSTRAINT app_release_client_ck CHECK (client_type IN ('android', 'ios', 'h5')),
    CONSTRAINT app_release_status_ck CHECK (status IN ('draft', 'published')),
    CONSTRAINT app_release_version_code_ck CHECK (version_code BETWEEN 20000101 AND 20991231)
);

CREATE TABLE app_runtime_config (
    id                  CHAR(26)       PRIMARY KEY,
    name                VARCHAR(64)    NOT NULL,
    client_type         VARCHAR(8)     NOT NULL,
    channel             VARCHAR(32)    NOT NULL,
    version_code_min    INT,
    version_code_max    INT,
    payload             JSONB          NOT NULL DEFAULT '{}',
    priority            INT            NOT NULL DEFAULT 0,
    status              VARCHAR(16)    NOT NULL,
    updated_at          TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT app_runtime_config_status_ck CHECK (status IN ('active', 'retired'))
);

CREATE TABLE announcement (
    id                  CHAR(26)       PRIMARY KEY,
    title               VARCHAR(128)   NOT NULL,
    body                TEXT           NOT NULL,
    client_types        JSONB          NOT NULL DEFAULT '[]',
    channels            JSONB          NOT NULL DEFAULT '[]',
    version_code_min    INT,
    version_code_max    INT,
    sort                INT            NOT NULL DEFAULT 0,
    status              VARCHAR(16)    NOT NULL,
    starts_at           TIMESTAMPTZ(3),
    ends_at             TIMESTAMPTZ(3),
    created_at          TIMESTAMPTZ(3) NOT NULL,
    updated_at          TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT announcement_status_ck CHECK (status IN ('draft', 'published', 'retired'))
);

CREATE TABLE inbox_message (
    id          CHAR(26)       PRIMARY KEY,
    user_id     CHAR(26)       NOT NULL,
    title       VARCHAR(128)   NOT NULL,
    body        TEXT           NOT NULL,
    read_at     TIMESTAMPTZ(3),
    created_at  TIMESTAMPTZ(3) NOT NULL
);
CREATE INDEX idx_inbox_message_user_created ON inbox_message (user_id, created_at);

CREATE TABLE chat_suggestion (
    id          CHAR(26)       PRIMARY KEY,
    text        VARCHAR(128)   NOT NULL,
    sort        INT            NOT NULL DEFAULT 0,
    status      VARCHAR(16)    NOT NULL,
    created_at  TIMESTAMPTZ(3) NOT NULL,
    updated_at  TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT chat_suggestion_status_ck CHECK (status IN ('active', 'retired'))
);

CREATE TABLE agent_prompt (
    key         VARCHAR(64)    PRIMARY KEY,
    name        VARCHAR(64)    NOT NULL,
    content     TEXT           NOT NULL,
    updated_at  TIMESTAMPTZ(3) NOT NULL,
    updated_by  VARCHAR(64),
    CONSTRAINT agent_prompt_content_ck CHECK (length(btrim(content)) > 0)
);

INSERT INTO agent_prompt (key, name, content, updated_at) VALUES
    ('intent.router', '意图路由', '识别意图、槽位和置信度。', CURRENT_TIMESTAMP),
    ('clarify', '追问', '槽位不足时向用户追问。', CURRENT_TIMESTAMP),
    ('reply.shop', '导购回复', '根据商品结果回复。', CURRENT_TIMESTAMP),
    ('reply.account', '账户回复', '回复账户、授权和统计。', CURRENT_TIMESTAMP),
    ('reply.order', '订单回复', '回复订单状态。', CURRENT_TIMESTAMP),
    ('reply.earn', '收益回复', '回复邀请和返佣。', CURRENT_TIMESTAMP),
    ('reply.support', '客服回复', '根据知识库或工单回复。', CURRENT_TIMESTAMP),
    ('reply.chitchat', '闲聊回复', '闲聊时简短回复。', CURRENT_TIMESTAMP);

CREATE FUNCTION app_user_guard_update() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.invite_code IS DISTINCT FROM OLD.invite_code THEN
        RAISE EXCEPTION 'invite_code is immutable';
    END IF;
    IF OLD.inviter_id IS NOT NULL AND NEW.inviter_id IS DISTINCT FROM OLD.inviter_id THEN
        RAISE EXCEPTION 'inviter_id is immutable';
    END IF;
    IF NEW.inviter_id IS NOT NULL AND NEW.inviter_id = NEW.id THEN
        RAISE EXCEPTION 'inviter_id cannot equal id';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER app_user_guard_update
    BEFORE UPDATE ON app_user
    FOR EACH ROW EXECUTE FUNCTION app_user_guard_update();
