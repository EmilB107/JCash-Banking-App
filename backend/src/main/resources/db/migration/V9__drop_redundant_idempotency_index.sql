-- uq_transfer_idempotency_sender_key on (sender_account_id, idempotency_key)
-- already serves lookups by sender_account_id, so this index only added
-- write cost to every transfer.
DROP INDEX idx_transfer_idempotency_sender_account;
