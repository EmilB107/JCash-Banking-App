package com.jcash.app.users;

import com.jcash.app.common.FreshTestDatabaseConfiguration;
import com.jcash.app.users.exception.MobileNumberAlreadyRegisteredException;
import com.jcash.app.users.exception.UserAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest(properties = {
        FreshTestDatabaseConfiguration.DATASOURCE_URL,
        FreshTestDatabaseConfiguration.CLEAN_ENABLED
})
@Import(FreshTestDatabaseConfiguration.class)
class UserRegistrationIntegrationTest {

    private static final long TIMEOUT_SECONDS = 10;
    private static final String PIN = "123456";

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

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
        Throwable failure = raceSecondRegistration(
                () -> userService.createUser("race@example.com", "09170000001", "First", PIN),
                () -> userService.createUser("Race@Example.com", "09170000002", "Second", PIN));

        assertThat(failure).isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    void concurrentRegistrationWithSameMobileNumberIsRejectedAsDuplicate() throws Exception {
        Throwable failure = raceSecondRegistration(
                () -> userService.createUser("first@example.com", "09170000001", "First", PIN),
                () -> userService.createUser("second@example.com", "+639170000001", "Second", PIN));

        assertThat(failure).isInstanceOf(MobileNumberAlreadyRegisteredException.class);
    }

    @Test
    void registrationStoresCanonicalMobileNumber() {
        userService.createUser("demo@example.com", " +639171234567 ", "Demo", PIN);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT mobile_number FROM users WHERE email = 'demo@example.com'", String.class))
                .isEqualTo("09171234567");
    }

    @Test
    void databaseRejectsNonCanonicalMobileNumber() {
        for (String mobileNumber : new String[] {"+639171234567", "9171234567", "0917123456a"}) {
            assertThatThrownBy(() -> userRepository.saveAndFlush(
                    new User("demo@example.com", mobileNumber, "Demo", "hash")))
                    .as("mobile number %s", mobileNumber)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // Runs the first registration in a transaction that stays open until the second
    // registration is blocked on the unique index, then commits it. Returns the
    // exception the second registration failed with.
    private Throwable raceSecondRegistration(
            Supplier<User> firstRegistration,
            Supplier<User> secondRegistration) throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch firstInserted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<User> first = executor.submit(() -> transaction.execute(status -> {
                User user = firstRegistration.get();
                firstInserted.countDown();
                await(releaseFirst);
                return user;
            }));
            await(firstInserted);

            // The second registration cannot see the uncommitted row, so it passes
            // the exists checks and blocks on the unique index until the first commits.
            Future<User> second = executor.submit(secondRegistration::get);
            awaitUserInsertBlocked();
            releaseFirst.countDown();

            assertThat(first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull();
            Throwable failure = catchThrowable(() -> second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertThat(failure).isInstanceOf(ExecutionException.class);
            assertThat(countRows("users")).isEqualTo(1);
            assertThat(countRows("accounts")).isEqualTo(1);
            return failure.getCause();
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
