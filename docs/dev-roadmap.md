# Development Roadmap

This is a suggested implementation order and status summary. Adjust it as dependencies and learning needs become clear.

1. **Foundation:** confirm Angular, Spring Boot, Maven Wrapper, PostgreSQL, JPA, and Flyway run locally. **Complete.**
2. **Data model:** design and migrate user/account tables and the ledger-related schema. **Complete through transaction details:** V1-V9 migrations define users, accounts, transaction entries, account versions, transfer idempotency records, normalized (case-insensitive) user emails, unique canonical mobile numbers, PIN hashes, login-lockout state, and transaction details with linked transfer entries.
3. **Core API:** account retrieval, validated demo cash-in, transaction history, and authenticated transfer business rules. **Complete for the current backend milestone.**
4. **Financial integrity:** use `BigDecimal`, transaction boundaries, concurrency protection, balanced ledger entries, optimistic locking, and idempotency. **Implemented and verified for transfers against PostgreSQL**, including rollback, concurrent duplicates, concurrent overspending, and deadlock-free opposite-direction transfers.
5. **Automated tests:** cover successful and failed transfers, insufficient funds, duplicate retries, concurrency, and authorization. **Implemented:** unit, MockMvc (authentication, CSRF, validation, and HTTP error mapping), and PostgreSQL-backed migration, rollback, and concurrency tests.
6. **Security:** complete credential (PIN) hashing, session-based login/logout, CSRF protection, endpoint protection, validation, and account ownership checks before exposing protected features. Registration, login by email or mobile number with PIN, the 3-attempt lockout, logout, the protected current-user endpoint, authenticated account access, transaction history, transfer sender/recipient ownership checks, profile editing, and PIN change (sharing the login lockout) are implemented.
7. **API tooling:** document endpoints with OpenAPI/Swagger UI and test them with Postman. **Complete:** Swagger UI (springdoc, `/swagger-ui/index.html`) and a Postman collection (`postman/JCash.postman_collection.json`) that runs the full API flow with tests, in the Postman app or with `npx newman run` (see [testing](testing.md#postman-collection)).
8. **Frontend integration:** build login, dashboard, transfer, and history views and connect them to the API. **Complete:** Tailwind CSS styling, the API layer (dev proxy, CSRF interceptor, session state), route guards, the public landing page, the register and login pages, a signed-in layout with a sidebar, the dashboard (balance, quick actions, and recent activity), the cash-in and send-money pages (with a confirm step and idempotency key), the paged transaction history with expandable details, and the profile page (view and edit details, change PIN) are implemented.
9. **End-to-end verification:** test transfers between separate users and verify database/ledger consistency. **Complete:** the Postman collection transfers between separate users and checks both sides, and `scripts/sql/verify-ledger.sql` checks ledger consistency in the database (see [testing](testing.md#end-to-end-verification)).
10. **Portfolio deployment:** deploy Angular, Spring Boot, and PostgreSQL using the selected hosting providers. **Deployed:** the frontend as a Render Static Site, the backend as a Render Web Service (Docker), and the database on Supabase (see [deployment](deployment.md)). Remaining: demo account and README screenshots.
11. **Extras:** GitHub Actions CI (see [testing](testing.md#continuous-integration)), Docker for the backend deployment, and Actuator for health checks and metrics. **Complete.**

## Completion criteria
- Users can register/log in and access only their own accounts.
- Users can view balances and transaction history and transfer simulated money through the backend API.
- Invalid transfers, self-transfers, unknown recipients, and insufficient balances are rejected.
- Transfers are atomic and protected against concurrent duplicate processing, concurrent overspending, deadlocks between opposite-direction transfers, and optimistic-lock conflicts.
- Repeated completed requests return the original result without duplicate ledger entries.
- Financial records are auditable and internally consistent.
- Automated unit, HTTP, and PostgreSQL-backed integration tests cover important success and failure paths, including migration, rollback, and concurrency.
- Public deployment is usable as a portfolio demo and clearly disclaims real-money use.
