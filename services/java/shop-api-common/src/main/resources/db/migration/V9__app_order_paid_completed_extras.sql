ALTER TABLE app_order
    ADD COLUMN paid_at TIMESTAMPTZ(3),
    ADD COLUMN completed_at TIMESTAMPTZ(3),
    ADD COLUMN extras TEXT;

ALTER TABLE app_order
    ADD CONSTRAINT app_order_extras_ck CHECK (extras IS NULL OR jsonb_typeof(extras::jsonb) = 'object');
