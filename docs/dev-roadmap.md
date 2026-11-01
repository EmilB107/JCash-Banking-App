# Development Roadmap

This is a suggested implementation order and status summary. Adjust it as dependencies and learning needs become clear.

1. **Foundation:** confirm Angular, Spring Boot, Maven Wrapper, PostgreSQL, JPA, and Flyway run locally. **Complete.**
2. **Data model:** design and migrate user/account tables and the ledger-related schema. **Complete through transaction details:** V1-V9 migrations define users, accounts, transaction entries, account versions, transfer idempotency records, normalized (case-insensitive) user emails, unique canonical mobile numbers, PIN hashes, login-lockout state, and transaction details with linked transfer entries.
3. **Core API:** account retrieval, validated demo cash-in, transaction history, and authenticated transfer business rules. **Complete for the current backend milestone.**
4. **Financial integrity:** use `BigDecimal`, transaction boundaries, concurrency protection, balanced ledger entries, optimistic locking, and idempotency. **Implemented and verified for transfers against PostgreSQL**, including rollback, concurrent duplicates, concurrent overspending, and deadlock-free opposite-direction transfers.
5. **Automated tests:** cover successful and failed transfers, insufficient funds, duplicate retries, concurrency, and authorization. **Implemented:** unit, MockMvc (authentication, CSRF, validation, and HTTP error mapping), and PostgreSQL-backed migration, rollback, and concurrency tests.
6. **Security:** complete credential (PIN) hashing, session-based login/logout, CSRF protection, endpoint protection, validation, and account ownership checks before exposing protected features. Registration, login by email or mobile number with PIN, the 3-attempt lockout, logout, the protected current-user endpoint, authenticated account access, transaction history, and transfer sender/recipient ownership checks are implemented.
7. **API tooling:** document endpoints with OpenAPI/Swagger UI and test them with Postman. **Swagger UI implemented** (springdoc, `/swagger-ui/index.html`); the Postman run is pending.
8. **Frontend integration:** build login, dashboard, transfer, and history views and connect them to the API. **In progress:** Tailwind CSS styling, the API layer (dev proxy, CSRF interceptor, session state), route guards, and the register and login pages are implemented; the dashboard is a placeholder.
9. **End-to-end verification:** test transfers between separate users and verify database/ledger consistency.
10. **Portfolio deployment:** deploy Angular, Spring Boot, and PostgreSQL using the selected hosting providers.
11. **Optional improvements:** GitHub Actions, WebSockets, Docker, Testcontainers/Playwright, Actuator, structured logging, correlation IDs, and monitoring.

## Completion criteria
- Users can register/log in and access only their own accounts.
- Users can view balances and transaction history and transfer simulated money through the backend API.
- Invalid transfers, self-transfers, unknown recipients, and insufficient balances are rejected.
- Transfers are atomic and protected against concurrent duplicate processing, concurrent overspending, deadlocks between opposite-direction transfers, and optimistic-lock conflicts.
- Repeated completed requests return the original result without duplicate ledger entries.
- Financial records are auditable and internally consistent.
- Automated unit, HTTP, and PostgreSQL-backed integration tests cover important success and failure paths, including migration, rollback, and concurrency.
- Public deployment is usable as a portfolio demo and clearly disclaims real-money use.
