ALTER TABLE users
    ADD CONSTRAINT chk_users_email_normalized
        CHECK (email = lower(btrim(email)));
