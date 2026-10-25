# Development Roadmap

This is a suggested implementation order, not a status report. Adjust it as dependencies and learning needs become clear.

1. **Foundation:** confirm Angular, Spring Boot, Maven Wrapper, PostgreSQL, JPA, and Flyway run locally.
2. **Data model:** design and migrate user/account tables and the ledger-related schema.
3. **Core API:** implement account retrieval and transaction history, then transfer business rules.
4. **Financial integrity:** use `BigDecimal`, transaction boundaries, concurrency protection, balanced ledger entries, and idempotency.
5. **Automated tests:** cover successful and failed transfers, insufficient funds, duplicate retries, concurrency, and authorization.
6. **Security:** implement password hashing, login, endpoint protection, validation, and account ownership checks before exposing protected features.
7. **API tooling:** document endpoints with OpenAPI/Swagger UI and test them with Postman.
8. **Frontend integration:** build login, dashboard, transfer, and history views and connect them to the API.
9. **End-to-end verification:** test transfers between separate users and verify database/ledger consistency.
10. **Portfolio deployment:** deploy Angular, Spring Boot, and PostgreSQL using the selected hosting providers.
11. **Optional improvements:** GitHub Actions, WebSockets, Docker, Testcontainers/Playwright, Actuator, structured logging, correlation IDs, and monitoring.

## Completion criteria
- Users can register/log in and access only their own accounts.
- Users can view balances and transaction history and transfer simulated money.
- Invalid transfers and insufficient balances are rejected.
- Transfers are atomic and protected against concurrent overspending and duplicate retries.
- Financial records are auditable and internally consistent.
- Automated tests cover important success and failure paths.
- Public deployment is usable as a portfolio demo and clearly disclaims real-money use.
