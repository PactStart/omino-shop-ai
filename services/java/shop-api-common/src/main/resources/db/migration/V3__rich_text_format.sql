ALTER TABLE announcement
    ADD COLUMN format VARCHAR(16) NOT NULL DEFAULT 'plain',
    ADD CONSTRAINT announcement_format_ck CHECK (format IN ('plain', 'html'));

ALTER TABLE inbox_message
    ADD COLUMN format VARCHAR(16) NOT NULL DEFAULT 'plain',
    ADD CONSTRAINT inbox_message_format_ck CHECK (format IN ('plain', 'html'));

ALTER TABLE app_release
    ADD COLUMN format VARCHAR(16) NOT NULL DEFAULT 'plain',
    ADD CONSTRAINT app_release_format_ck CHECK (format IN ('plain', 'html'));
