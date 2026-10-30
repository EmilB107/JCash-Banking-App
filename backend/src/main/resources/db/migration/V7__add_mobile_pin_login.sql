-- Existing users have no mobile number and their password hash cannot become a PIN hash,
-- so refuse to migrate a non-empty users table instead of inventing values.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM users) THEN
        RAISE EXCEPTION 'V7 requires an empty users table: existing users have no mobile number or PIN';
    END IF;
END
$$;

ALTER TABLE users
    RENAME COLUMN password_hash TO pin_hash;

ALTER TABLE users
    ADD COLUMN mobile_number VARCHAR(11) NOT NULL,
    ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMPTZ,
    ADD CONSTRAINT uq_users_mobile_number
        UNIQUE (mobile_number),
    ADD CONSTRAINT chk_users_mobile_number_format
        CHECK (mobile_number ~ '^09[0-9]{9}$'),
    ADD CONSTRAINT chk_users_failed_login_attempts_non_negative
        CHECK (failed_login_attempts >= 0);
