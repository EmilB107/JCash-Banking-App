-- Ledger consistency check for JCash.
--
-- Read-only: it only runs SELECTs, so it's safe on the development database.
-- Each check counts the rows that break one rule. 0 violations = PASS.
--
--   psql -h localhost -U jcash_banking_user -d jcash_banking_db -f scripts/sql/verify-ledger.sql
--
-- The rules follow from how the app moves money:
--   * every account starts at 0.00, and only cash-in creates money
--   * a transfer only moves money, as one DEBIT plus one CREDIT of the same amount
--   * balance_after on each entry is the running balance at that point

-- Each account's credits minus debits, the expected balance.
WITH ledger AS (
    SELECT account_id,
           SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END) AS net
    FROM transaction_entries
    GROUP BY account_id
),

-- Running balance per account, oldest entry first (id breaks timestamp ties).
running AS (
    SELECT id,
           balance_after,
           SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END)
               OVER (PARTITION BY account_id ORDER BY created_at, id) AS expected_after
    FROM transaction_entries
),

-- Both halves of each transfer side by side. A missing half shows up as NULL.
transfer_pairs AS (
    SELECT d.transfer_reference AS debit_ref,
           c.transfer_reference AS credit_ref,
           d.account_id         AS debit_account,
           d.counterparty_account_id AS debit_counterparty,
           c.account_id         AS credit_account,
           c.counterparty_account_id AS credit_counterparty,
           d.amount             AS debit_amount,
           c.amount             AS credit_amount,
           d.currency           AS debit_currency,
           c.currency           AS credit_currency
    FROM (SELECT * FROM transaction_entries WHERE transaction_type = 'TRANSFER' AND direction = 'DEBIT') d
    FULL JOIN (SELECT * FROM transaction_entries WHERE transaction_type = 'TRANSFER' AND direction = 'CREDIT') c
        ON c.transfer_reference = d.transfer_reference
),

checks AS (
    SELECT 1 AS n,
           'Account balance = credits - debits' AS rule,
           (SELECT COUNT(*) FROM accounts a
                LEFT JOIN ledger l ON l.account_id = a.id
            WHERE a.balance <> COALESCE(l.net, 0)) AS violations

    UNION ALL
    SELECT 2, 'Each entry''s balance_after = running balance',
           (SELECT COUNT(*) FROM running WHERE balance_after <> expected_after)

    UNION ALL
    SELECT 3, 'Every transfer has both a DEBIT and a CREDIT',
           (SELECT COUNT(*) FROM transfer_pairs WHERE debit_ref IS NULL OR credit_ref IS NULL)

    UNION ALL
    SELECT 4, 'Both halves of a transfer have the same amount and currency',
           (SELECT COUNT(*) FROM transfer_pairs
            WHERE debit_amount <> credit_amount OR debit_currency <> credit_currency)

    UNION ALL
    SELECT 5, 'Transfer halves point at each other, between different accounts',
           (SELECT COUNT(*) FROM transfer_pairs
            WHERE debit_counterparty <> credit_account
               OR credit_counterparty <> debit_account
               OR debit_account = credit_account)

    UNION ALL
    SELECT 6, 'Entry currency matches its account currency',
           (SELECT COUNT(*) FROM transaction_entries e
                JOIN accounts a ON a.id = e.account_id
            WHERE e.currency <> a.currency)

    UNION ALL
    SELECT 7, 'Every user has an account',
           (SELECT COUNT(*) FROM users u
            WHERE NOT EXISTS (SELECT 1 FROM accounts a WHERE a.user_id = u.id))

    UNION ALL
    -- Money is conserved: transfers move it, so the sum of all balances
    -- must equal the sum of all cash-ins. Counts as 1 violation if not.
    SELECT 8, 'Total of all balances = total cash-in',
           (SELECT CASE WHEN
                (SELECT COALESCE(SUM(balance), 0) FROM accounts)
                = (SELECT COALESCE(SUM(amount), 0) FROM transaction_entries
                   WHERE transaction_type = 'CASH_IN')
                THEN 0 ELSE 1 END)

    UNION ALL
    -- Each completed idempotency record is exactly one transfer, so a retry
    -- that wrote a second ledger pair would make these counts differ.
    SELECT 9, 'Transfers recorded = completed idempotency keys',
           (SELECT ABS(
                (SELECT COUNT(*) FROM transaction_entries
                 WHERE transaction_type = 'TRANSFER' AND direction = 'DEBIT')
                - (SELECT COUNT(*) FROM transfer_idempotency_keys WHERE completed)))
)

SELECT n AS "#",
       rule,
       violations,
       CASE WHEN violations = 0 THEN 'PASS' ELSE 'FAIL' END AS result
FROM checks
ORDER BY n;

-- Totals, for context.
SELECT (SELECT COUNT(*) FROM users) AS users,
       (SELECT COUNT(*) FROM transaction_entries WHERE transaction_type = 'CASH_IN') AS cash_ins,
       (SELECT COUNT(*) FROM transaction_entries
        WHERE transaction_type = 'TRANSFER' AND direction = 'DEBIT') AS transfers,
       (SELECT COALESCE(SUM(balance), 0) FROM accounts) AS total_balance;
