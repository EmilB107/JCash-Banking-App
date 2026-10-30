package com.jcash.app.users;

import com.jcash.app.common.FreshTestDatabaseConfiguration;
import com.jcash.app.users.exception.UserAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        FreshTestDatabaseConfiguration.DATASOURCE_URL,
        FreshTestDatabaseConfiguration.CLEAN_ENABLED
})
@Import(FreshTestDatabaseConfiguration.class)
class UserRegistrationIntegrationTest {

    private static final long TIMEOUT_SECONDS = 10;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetData() {
        jdbcTemplate.update("DELETE FROM transfer_idempotency_keys");
        jdbcTemplate.update("DELETE FROM transaction_entries");
        jdbcTemplate.update("DELETE FROM accounts");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void concurrentRegistrationWithSameEmailIsRejectedAsDuplicate() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch firstInserted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<User> first = executor.submit(() -> transaction.execute(status -> {
                User user = userService.createUser("race@example.com", "First", "password123");
                firstInserted.countDown();
                await(releaseFirst);
                return user;
            }));
            await(firstInserted);

            // The second registration cannot see the uncommitted row, so it passes
            // existsByEmail and blocks on the unique index until the first commits.
            Future<User> second = executor.submit(() ->
                    userService.createUser("Race@Example.com", "Second", "password123"));
            awaitUserInsertBlocked();
            releaseFirst.countDown();

            assertThat(first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).getEmail())
                    .isEqualTo("race@example.com");
            assertThatThrownBy(() -> second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(UserAlreadyExistsException.class);
            assertThat(countRows("users")).isEqualTo(1);
            assertThat(countRows("accounts")).isEqualTo(1);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    private void awaitUserInsertBlocked() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM pg_locks l
                JOIN pg_stat_activity a ON a.pid = l.pid
                WHERE NOT l.granted
                  AND a.datname = current_database()
                  AND a.query ILIKE 'insert into users%'
                """, Long.class) == 0) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("Second registration never blocked on the first");
            }
            Thread.sleep(20);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private long countRows(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }
}
