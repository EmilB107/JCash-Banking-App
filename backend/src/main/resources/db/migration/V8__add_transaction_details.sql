-- Existing transfer entries cannot be linked to their counterparty reliably,
-- so refuse to migrate them instead of guessing.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM transaction_entries WHERE transaction_type = 'TRANSFER') THEN
        RAISE EXCEPTION 'V8 cannot backfill counterparty details for existing TRANSFER entries';
    END IF;
END
$$;

ALTER TABLE transaction_entries
    ADD COLUMN details VARCHAR(255),
    ADD COLUMN counterparty_account_id UUID,
    ADD COLUMN transfer_reference UUID;

UPDATE transaction_entries
    SET details = 'Cash in'
    WHERE transaction_type = 'CASH_IN';

ALTER TABLE transaction_entries
    ALTER COLUMN details SET NOT NULL,
    ADD CONSTRAINT fk_transaction_entries_counterparty_account
        FOREIGN KEY (counterparty_account_id) REFERENCES accounts (id),
    ADD CONSTRAINT chk_transaction_entries_details_not_blank
        CHECK (btrim(details) <> ''),
    ADD CONSTRAINT chk_transaction_entries_transfer_link
        CHECK (
            (transaction_type = 'TRANSFER'
                AND counterparty_account_id IS NOT NULL
                AND transfer_reference IS NOT NULL)
            OR
            (transaction_type <> 'TRANSFER'
                AND counterparty_account_id IS NULL
                AND transfer_reference IS NULL)
        ),
    -- One DEBIT and one CREDIT per transfer; also indexes lookups by reference.
    ADD CONSTRAINT uq_transaction_entries_transfer_reference_direction
        UNIQUE (transfer_reference, direction);
