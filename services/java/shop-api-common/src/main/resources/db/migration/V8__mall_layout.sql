-- 商城 banner 与金刚区。不建外键。点击类型不加 CHECK，加类型时不用迁移。

CREATE TABLE mall_zone (
    code         VARCHAR(16)    PRIMARY KEY,
    columns      INT,
    interval_ms  INT,
    updated_at   TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT mall_zone_columns_ck CHECK (columns IS NULL OR columns BETWEEN 4 AND 6),
    CONSTRAINT mall_zone_interval_ck CHECK (interval_ms IS NULL OR interval_ms BETWEEN 0 AND 60000)
);

CREATE TABLE mall_category (
    id         CHAR(26)       PRIMARY KEY,
    name       VARCHAR(32)    NOT NULL,
    sort       INT            NOT NULL DEFAULT 0,
    status     VARCHAR(16)    NOT NULL,
    created_at TIMESTAMPTZ(3) NOT NULL,
    updated_at TIMESTAMPTZ(3) NOT NULL
);

CREATE TABLE mall_entry (
    id                CHAR(26)       PRIMARY KEY,
    zone              VARCHAR(16)    NOT NULL,
    category_id       CHAR(26),
    title             VARCHAR(32),
    image_object_key  VARCHAR(512)   NOT NULL,
    sort              INT            NOT NULL DEFAULT 0,
    status            VARCHAR(16)    NOT NULL,
    starts_at         TIMESTAMPTZ(3),
    ends_at           TIMESTAMPTZ(3),
    client_types      JSONB          NOT NULL DEFAULT '[]',
    channels          JSONB          NOT NULL DEFAULT '[]',
    action_type       VARCHAR(32)    NOT NULL,
    action_payload    JSONB          NOT NULL DEFAULT '{}',
    created_at        TIMESTAMPTZ(3) NOT NULL,
    updated_at        TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT mall_entry_ends_ck CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at)
);

CREATE INDEX mall_entry_zone_status_sort_idx ON mall_entry (zone, status, sort);

INSERT INTO mall_zone (code, columns, interval_ms, updated_at) VALUES
    ('banner', NULL, 3000, CURRENT_TIMESTAMP),
    ('nav', 5, NULL, CURRENT_TIMESTAMP);
