ALTER TABLE accounts
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE transaction_entries
    DROP CONSTRAINT chk_transaction_entries_type;

ALTER TABLE transaction_entries
    ADD CONSTRAINT chk_transaction_entries_type
        CHECK (transaction_type IN ('CASH_IN', 'TRANSFER'));

CREATE TABLE transfer_idempotency_keys (
    id UUID PRIMARY KEY,
    sender_account_id UUID NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    recipient_account_id UUID,
    sender_balance_after NUMERIC(19, 2),
    recipient_balance_after NUMERIC(19, 2),
    currency VARCHAR(3),
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transfer_idempotency_sender_account
        FOREIGN KEY (sender_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_transfer_idempotency_recipient_account
        FOREIGN KEY (recipient_account_id) REFERENCES accounts (id),
    CONSTRAINT uq_transfer_idempotency_sender_key
        UNIQUE (sender_account_id, idempotency_key),
    CONSTRAINT chk_transfer_idempotency_amount_positive
        CHECK (amount > 0),
    CONSTRAINT chk_transfer_idempotency_sender_balance_non_negative
        CHECK (sender_balance_after IS NULL OR sender_balance_after >= 0),
    CONSTRAINT chk_transfer_idempotency_recipient_balance_non_negative
        CHECK (recipient_balance_after IS NULL OR recipient_balance_after >= 0),
    CONSTRAINT chk_transfer_idempotency_currency
        CHECK (currency IS NULL OR currency = upper(currency)),
    CONSTRAINT chk_transfer_idempotency_completed_payload
        CHECK (
            (completed = FALSE
                AND recipient_account_id IS NULL
                AND sender_balance_after IS NULL
                AND recipient_balance_after IS NULL
                AND currency IS NULL)
            OR
            (completed = TRUE
                AND recipient_account_id IS NOT NULL
                AND sender_balance_after IS NOT NULL
                AND recipient_balance_after IS NOT NULL
                AND currency IS NOT NULL)
        )
);

CREATE INDEX idx_transfer_idempotency_sender_account
    ON transfer_idempotency_keys (sender_account_id);
