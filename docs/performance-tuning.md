# Performance Tuning

Assessment section I asks for tuning across four layers: application code, data access, container configuration, and JVM runtime settings. The "Required" line also asks for monitoring operating-system performance and for tuning at the language level. Each section below says what was set and why.

## 1. Application code

**History paging.** `GET /api/transactions/me` returns one page (`?page=0&size=20`, maximum 100) instead of the whole history. Response size, memory, and query time stay constant as a user's history grows. The maximum of 100 stops a client from asking for everything at once.

**Stable order.** Pages are sorted by `created_at DESC, id DESC`. The `id` tie-breaker means two entries with the same timestamp keep a fixed order, so an entry never appears on two pages or is skipped.

**No open session in view.** `spring.jpa.open-in-view=false` closes the database session when the service method returns. Lazy data can't be loaded by accident while JSON is being written, and connections go back to the pool sooner.

## 2. Data access

**Lazy account on transaction entries.** `TransactionEntry.account` is `FetchType.LAZY`. It used to be eager (the JPA default for `@ManyToOne`), so every history request also loaded the `Account` and its `User`, even though the response only needs the account ID. Hibernate can read the ID of a lazy reference without loading it.

**Filter by account ID.** The service first looks up the caller's account ID (`AccountRepository.findIdByUserEmail`, one query that returns only the ID). It then queries entries with `account_id = ?`. Filtering through a join on the user stopped PostgreSQL from using the index order, so it read and sorted every entry of the account.

Measured with Hibernate statistics (`TransactionHistoryIntegrationTest.historyPageUsesAFixedNumberOfQueries`):

| | Queries per history page |
|---|---|
| Before (eager account, join on user) | 4 |
| After | 3: account ID, the page, and the total count |

**Indexes.** These were checked with `EXPLAIN ANALYZE` on 200 users × 1,000 entries (200,000 rows) in the test database.

| Query | Index used | Result |
|---|---|---|
| History page, filtered through the user join (before) | `idx_transaction_entries_account_created_at` (bitmap scan) | Read all 1,000 entries of the account, then sorted them |
| History page, filtered by `account_id` (after) | same index, ordered index scan | Read 21 rows; incremental sort only on equal timestamps; about 0.1 ms |
| History count | same index (bitmap scan on `account_id`) | Reads index entries for one account only; about 0.5 ms for 1,000 entries |
| Login, account ID by email | `users_email_key`, `uq_users_mobile_number` (unique) | With 200 users PostgreSQL chooses a sequential scan, which is cheaper at that size; the unique indexes take over as the table grows |
| Account by user | `idx_accounts_user_id` | Index lookup |
| Idempotency key (20,000 rows) | `uq_transfer_idempotency_sender_key` (unique) | Index lookup, about 0.05 ms |

No new index was needed. I also tried an index on `(account_id, created_at DESC, id DESC)`. It removed the small incremental sort but didn't change the number of rows read, so it was left out.

**Redundant index dropped (V9).** `idx_transfer_idempotency_sender_account` on `(sender_account_id)` duplicated the leading column of the unique constraint `uq_transfer_idempotency_sender_key` on `(sender_account_id, idempotency_key)`. PostgreSQL uses the unique index for both lookups, so the extra index only added work to every transfer insert. Migration `V9__drop_redundant_idempotency_index.sql` removes it.

**Connection pool (HikariCP)** in `application.properties`:

| Setting | Value | Why |
|---|---|---|
| `maximum-pool-size` | 10 | Each query holds a connection only briefly. A pool much larger than the database's CPU count adds contention, not throughput, and small hosted PostgreSQL plans also limit total connections. |
| `minimum-idle` | 10 | A fixed-size pool (HikariCP's recommendation) avoids opening connections under load. |
| `connection-timeout` | 5 s | If the pool is exhausted, the request fails fast with an error instead of hanging for the default 30 s. |
| `max-lifetime` | 30 min | Connections are replaced before a database or network timeout closes them. |
| `pool-name` | `jcash-pool` | Makes the pool easy to find in logs and metrics. |
| `initialization-fail-timeout` | 60 s | Retries the first connection instead of failing at once; on Render's free plan the first encrypted connection can time out (see [deployment](deployment.md#backend-render)). |

## 3. Container configuration (embedded Tomcat)

Spring Boot runs Tomcat inside the jar. Tomcat accepts HTTP requests and runs each one on a worker thread.

| Setting | Value | Why |
|---|---|---|
| `server.tomcat.threads.max` | 50 | Every request that touches the database needs one of the 10 pooled connections. The default 200 threads would mostly wait for a connection while using memory. 50 still leaves room for requests that wait on BCrypt or the network. |
| `server.tomcat.threads.min-spare` | 10 | Threads ready for a burst without start-up cost. |
| `server.tomcat.accept-count` | 100 | Requests queued once all threads are busy; beyond that, connections are refused instead of piling up. |
| `server.tomcat.connection-timeout` | 10 s | A slow or idle client can't hold a thread for long. |
| `server.compression.*` | JSON over 1 KB, gzip | Measured: the OpenAPI spec dropped from 10,460 to 1,300 bytes. History pages shrink similarly, which helps on mobile connections. |

## 4. JVM runtime settings

These are set in `backend/pom.xml` (`spring-boot-maven-plugin` → `jvmArguments`), so every start script uses them. To run the jar directly:

```bash
java -Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
     -XX:+HeapDumpOnOutOfMemoryError -jar backend/target/backend-0.0.1-SNAPSHOT.jar
```

| Flag | Why |
|---|---|
| `-Xms256m` | Starts with enough heap for Spring, Hibernate, and Tomcat, so the JVM doesn't grow the heap repeatedly during start-up. |
| `-Xmx512m` | Caps memory. Without it, the JVM may take a quarter of the machine's RAM. Lower it if the host has less memory; the heap must fit well inside the host's limit. |
| `-XX:+UseG1GC` | G1 balances throughput and short pauses for a heap of this size. It is the default on most machines; stating it keeps the choice explicit. |
| `-XX:MaxGCPauseMillis=200` | G1's pause-time target (its default value, written down). |
| `-XX:+HeapDumpOnOutOfMemoryError` | If memory runs out, a heap dump is written so the cause can be found. |

The deployed container doesn't use these: it runs the jar directly, with the flags in `backend/Dockerfile`, tuned for Render's free plan (512 MB, 0.1 CPU). There, `-XX:MaxRAMPercentage=75` sizes the heap from the container's memory (a fixed `-Xmx512m` would leave nothing for the rest of the JVM), and `-XX:+UseSerialGC`, `-XX:TieredStopAtLevel=1`, and a Class Data Sharing archive cut start-up CPU about 4× (see [deployment](deployment.md#backend-render)).

## 5. Monitoring (operating system and JVM)

Spring Boot Actuator is enabled on the app's port, so it stays reachable when hosted. It has its own **operator account**, separate from app users. The account isn't in the database, and its username and password come from the `ACTUATOR_USERNAME` and `ACTUATOR_PASSWORD` environment variables (`.env` locally, the host's settings when deployed). The backend refuses to start if they are missing or blank. Setup steps are in [Backend setup](backend-setup.md#actuator-operator-account). See `ActuatorSecurityConfiguration`.

| Endpoint | Access | Shows |
|---|---|---|
| `/actuator/health` | Public | `{"status":"UP"}` only |
| `/actuator/health` | Operator account | Also the database and disk-space details |
| `/actuator/metrics` | Operator account | List of available metric names |
| `/actuator/metrics/{name}` | Operator account | One metric, for example `/actuator/metrics/system.cpu.usage` |

The operator account uses HTTP Basic: a browser shows a username/password prompt, and from the command line use `curl -u "$ACTUATOR_USERNAME:$ACTUATOR_PASSWORD" http://localhost:8080/actuator/metrics`. It never creates a session. An app login doesn't grant access to metrics, and the operator account can't use the app's API. `ActuatorSecurityIntegrationTest` covers each of these rules.

Useful metric names:
- **Operating system:** `system.cpu.usage`, `system.cpu.count`, `system.load.average.1m`, `process.cpu.usage`, `process.uptime`, `disk.free`
- **JVM:** `jvm.memory.used`, `jvm.memory.max`, `jvm.gc.pause`, `jvm.threads.live`
- **Connection pool:** `hikaricp.connections.active`, `hikaricp.connections.pending`, `hikaricp.connections.timeout`
- **Requests:** `http.server.requests` (count and timing per endpoint, for example `?tag=uri:/api/transactions/me`)

These show whether the settings above fit. For example, `hikaricp.connections.pending` above 0 means requests wait for a connection, and a growing `jvm.gc.pause` means the heap is too small. For a live view while demonstrating, the JDK's `jconsole` or `jcmd <pid> GC.heap_info` work too, along with operating-system tools such as `top` or Task Manager.

## 6. Language level

These choices were already in the code; they're listed here because the assessment asks for language-level tuning.

- **`BigDecimal` for money**, never `double`/`float`. Binary floating point can't represent values like 0.10 exactly, and rounding errors in a balance are a correctness bug, not just a precision issue.
- **Regular expressions compiled once.** `MobileNumberNormalizer` keeps a `static final Pattern`. `String.matches` would compile the regex again on every call.
- **Immutable records for DTOs** (`TransactionEntryResponse`, `TransactionHistoryPage`). They are cheap to create, safe to share between threads, and need no defensive copies.
- **Constants as `static final`** (for example the sort order in `TransactionHistoryService`, and `LoginService.MAX_FAILED_ATTEMPTS`), so they are built once, not on every request.
- **Streams only for simple mapping** (entries to DTOs). There is no boxing in hot paths and no repeated string concatenation in loops.
- **BCrypt is slow on purpose.** PIN checks use BCrypt's default cost of 10 (tens of milliseconds per login). This is the most CPU-heavy operation in the app, and it is kept that way deliberately: lowering the cost would speed up login but make leaked hashes easier to crack.
