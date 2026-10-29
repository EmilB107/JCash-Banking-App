CREATE TABLE transaction_entries (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    balance_after NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transaction_entries_account
        FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT chk_transaction_entries_amount_positive
        CHECK (amount > 0),
    CONSTRAINT chk_transaction_entries_balance_after_non_negative
        CHECK (balance_after >= 0),
    CONSTRAINT chk_transaction_entries_currency
        CHECK (currency = upper(currency)),
    CONSTRAINT chk_transaction_entries_type
        CHECK (transaction_type IN ('CASH_IN')),
    CONSTRAINT chk_transaction_entries_direction
        CHECK (direction IN ('CREDIT', 'DEBIT'))
);

CREATE INDEX idx_transaction_entries_account_created_at
    ON transaction_entries (account_id, created_at DESC);
