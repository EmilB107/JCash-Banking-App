# Database Design

This document records the intended data model. Final table names, columns, constraints, and relationships should be confirmed in the Flyway migrations as they are implemented.

## Core concepts
- **User:** identity and password hash; never store plaintext passwords.
- **Account:** wallet owner, currency, and any cached/current balance representation.
- **Transfer:** sender, recipient, amount, status, timestamps, and idempotency key or equivalent request identity.
- **Ledger entry:** immutable debit/credit records linked to a transfer and account.

## Integrity rules
- Use primary and foreign keys, appropriate unique constraints, and check constraints (for example, positive transfer amounts).
- Use indexes for common account, transfer, and transaction-history lookups.
- Record each completed transfer as balanced debit and credit entries in the same database transaction.
- Do not edit or delete posted ledger entries to rewrite history; use corrective entries if the model requires adjustments.
- Define whether account balances are derived from ledger entries or maintained as a cached value. If cached, reconcile them against the ledger.
- Store monetary values with a suitable exact numeric type and map them to Java `BigDecimal`, not floating-point types.
- Make idempotency keys unique in the appropriate scope so a retry cannot post a duplicate transfer.

## Example
A successful ₱500 transfer from Alice to Bob produces a ₱500 debit for Alice and a ₱500 credit for Bob. Both entries and the transfer status must commit together or roll back together.

## Concurrency
Two ₱800 transfers must not both succeed against a ₱1,000 available balance. Design and test transaction isolation/locking and balance checks together; a pre-check alone is not sufficient protection against concurrent requests.
