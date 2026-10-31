# Database Design

This document records the implemented data model. Flyway migrations are the source of truth for table names, columns, constraints, and relationships.

## Core concepts
- **User:** email, mobile number, display name, PIN hash, and login-lockout state; emails are stored trimmed and lowercase, and V6 enforces this with a check constraint so uniqueness is case-insensitive; never store plaintext PINs.
- **Account:** wallet owner, currency, current balance, and JPA optimistic-lock version.
- **Transfer idempotency record:** sender-scoped request key, original request payload, completion state, recipient, and resulting balances.
- **Ledger entry:** immutable debit/credit record linked to an account, with a details description and, for transfers, the counterparty account and a reference shared by both sides.

## Implemented transfer schema
`V5__add_transfer_support.sql` adds the following without modifying earlier migrations:

- `accounts.version BIGINT NOT NULL DEFAULT 0`, mapped by JPA `@Version`.
- `TRANSFER` as an allowed `transaction_entries.transaction_type`.
- `transfer_idempotency_keys`, with foreign keys to sender and optional recipient accounts.
- A unique constraint on `(sender_account_id, idempotency_key)`, so a key can be retried safely for one sender but may be used independently by another sender.
- Exact monetary columns using `NUMERIC(19,2)` and a positive amount check.
- Completion checks requiring either an entirely empty pending payload or all completed response fields.
- Uppercase currency validation and non-negative stored post-transfer balances.

The idempotency entity stores the completed response data so a retry can return the original result without creating new account changes or ledger entries. JPA entities are persistence models only; transfer responses use a DTO.

## Implemented mobile and PIN login schema
`V7__add_mobile_pin_login.sql` changes `users`:

- Refuses to run if `users` already has rows, because existing users have no mobile number and a password hash cannot become a PIN hash.
- Renames `password_hash` to `pin_hash` (BCrypt hash of the 6-digit PIN).
- Adds `mobile_number VARCHAR(11) NOT NULL`, unique (`uq_users_mobile_number`) and checked against the canonical Philippine format `^09[0-9]{9}$`. `+639XXXXXXXXX` input is converted to `09XXXXXXXXX` before it is stored or looked up.
- Adds `failed_login_attempts INTEGER NOT NULL DEFAULT 0` (non-negative) and nullable `locked_until TIMESTAMPTZ`. The limit (3) and lock duration (5 minutes) live in `LoginService`, not in the schema.

Registration translates a unique violation on `users_email_key` or `uq_users_mobile_number` into HTTP 409, so two simultaneous registrations for the same email or mobile number cannot produce HTTP 500.

## Integrity rules
- Use primary and foreign keys, appropriate unique constraints, and check constraints.
- Record each completed transfer as one TRANSFER/DEBIT and one TRANSFER/CREDIT entry in the same database transaction.
- Account updates, both ledger entries, and the idempotency record must commit or roll back together.
- Do not edit or delete posted ledger entries to rewrite history; use corrective entries if the model requires adjustments.
- Store monetary values with exact numeric types mapped to Java `BigDecimal`, not floating-point types.
- Require a nonblank idempotency key no longer than 255 characters at the service boundary.
- Derive the sender from the authenticated user; never trust a sender ID supplied by a client.
- Keep entities out of API responses and expose only transfer DTO fields.

## Example
A successful PHP 500 transfer from Alice to Bob debits Alice from her current balance, credits Bob, and writes these entries:

- Alice: `TRANSFER`, `DEBIT`, `500.00`, balance after the debit.
- Bob: `TRANSFER`, `CREDIT`, `500.00`, balance after the credit.

The idempotency record is marked completed with both balances and the recipient account. Both account updates, both ledger entries, and the idempotency record commit together or roll back together.

## Concurrency
Both the sender and recipient account rows are locked with `PESSIMISTIC_WRITE` for the transfer transaction. The service resolves both users first, then locks the two account rows in ascending user-ID order. Because every transfer acquires locks in the same order, opposite-direction transfers such as A→B and B→A wait on each other instead of deadlocking. Self-transfers are rejected before any lock is taken.

Holding the sender lock serializes requests from the same sender while the idempotency record is checked and reserved, preventing two concurrent requests with the same sender/key from both applying a transfer, and preventing concurrent transfers from overspending the sender. Account `@Version` remains enabled as a second integrity check. An optimistic-lock conflict is returned as HTTP 409 `OPTIMISTIC_LOCK_CONFLICT`, and a lock-acquisition failure (for example a lock timeout or a database-detected deadlock) is returned as HTTP 409 `LOCK_CONFLICT`; neither produces a success-shaped response.

Recipient and sender account changes are made through `Account.debit` and `Account.credit`. The database balance check and the service-level insufficient-funds check both protect the non-negative invariant.

These behaviours are verified against PostgreSQL by `TransferIntegrationTest`: concurrent same-key requests, concurrent overspending, interleaved opposite-direction transfers, and rollback after a database write failure.

## Implemented transaction details schema
`V8__add_transaction_details.sql` changes `transaction_entries`:

- Refuses to run if TRANSFER entries already exist, because their counterparty cannot be reconstructed reliably.
- Adds `details VARCHAR(255) NOT NULL` (existing cash-in rows are backfilled with `Cash in`) and rejects blank details.
- Adds `counterparty_account_id`, a foreign key to `accounts`, and `transfer_reference UUID`. A check constraint requires both for TRANSFER entries and neither for other types.
- Adds a unique constraint on `(transfer_reference, direction)`, so one transfer has exactly one DEBIT and one CREDIT entry; it also indexes lookups by reference.

Details are written once, when the money moves, as a snapshot (`Transfer to Bob Santos (09170000002)`), so later name changes do not rewrite history. The counterparty account is stored but not yet returned by the API.

## Migration discipline
The database starts with the existing V1-V4 schema migrations, then applies V5 for transfer support, V6 to enforce normalized user emails, V7 for mobile-number and PIN login, V8 for transaction details and transfer links, and V9 to drop a redundant idempotency index (see [performance tuning](performance-tuning.md)). Once a Flyway migration has been applied, do not edit it; create a new versioned migration for future changes. Hibernate runs with `ddl-auto=validate`, so fresh-database migration correctness and entity/schema alignment must be checked together. The PostgreSQL integration tests do this on every run by cleaning the dedicated test database and applying V1-V9 from scratch before the Spring context validates the entities.
