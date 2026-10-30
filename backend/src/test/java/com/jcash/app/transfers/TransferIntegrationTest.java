package com.jcash.app.transfers;

import com.jcash.app.accounts.Account;
import com.jcash.app.accounts.AccountRepository;
import com.jcash.app.common.FreshTestDatabaseConfiguration;
import com.jcash.app.security.DatabaseUserDetailsService;
import com.jcash.app.transfers.dto.TransferRequest;
import com.jcash.app.transfers.dto.TransferResponse;
import com.jcash.app.transfers.exception.InsufficientFundsException;
import com.jcash.app.users.User;
import com.jcash.app.users.UserRepository;
import com.jcash.app.users.UserService;
import com.jcash.app.users.exception.UserAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
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
class TransferIntegrationTest {

    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";

    @Autowired
    private TransferService transferService;

    @Autowired
    private UserService userService;

    @Autowired
    private DatabaseUserDetailsService userDetailsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetData() {
        jdbcTemplate.update("DELETE FROM transfer_idempotency_keys");
        jdbcTemplate.update("DELETE FROM transaction_entries");
        jdbcTemplate.update("DELETE FROM accounts");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void freshDatabaseAppliesAllMigrations() {
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT version, success FROM flyway_schema_history "
                        + "WHERE version IS NOT NULL ORDER BY installed_rank");

        assertThat(history)
                .extracting(row -> row.get("version"))
                .containsExactly("1", "2", "3", "4", "5", "6");
        assertThat(history)
                .extracting(row -> row.get("success"))
                .containsOnly(true);
    }

    @Test
    void databaseRejectsNonNormalizedEmail() {
        assertThatThrownBy(() -> userRepository.saveAndFlush(
                new User("Mixed@Example.com", "Mixed", "hash")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void mixedCaseEmailsWorkAcrossRegistrationLoginAndTransfer() {
        createUserWithBalance(ALICE, "100.00");

        User carol = userService.createUser("  Carol@Example.COM ", "Carol", "password123");

        assertThat(carol.getEmail()).isEqualTo("carol@example.com");
        assertThatThrownBy(() -> userService.createUser(
                "CAROL@example.com", "Carol Again", "password123"))
                .isInstanceOf(UserAlreadyExistsException.class);
        assertThat(userDetailsService.loadUserByUsername("CAROL@EXAMPLE.COM").getUsername())
                .isEqualTo("carol@example.com");

        TransferResponse response = transfer(ALICE, "key-1", "Carol@example.com", "25.00");

        assertThat(response.recipientEmail()).isEqualTo("carol@example.com");
        assertThat(balanceOf("carol@example.com")).isEqualByComparingTo("25.00");
        assertThat(balanceOf(ALICE)).isEqualByComparingTo("75.00");
    }

    @Test
    void successfulTransferPersistsBalancesEntriesAndCompletedIdempotencyRecord() {
        createUserWithBalance(ALICE, "100.00");
        createUserWithBalance(BOB, "50.00");

        TransferResponse response = transfer(ALICE, "key-1", BOB, "25.00");

        assertThat(response.senderBalanceAfter()).isEqualByComparingTo("75.00");
        assertThat(response.recipientBalanceAfter()).isEqualByComparingTo("75.00");
        assertThat(balanceOf(ALICE)).isEqualByComparingTo("75.00");
        assertThat(balanceOf(BOB)).isEqualByComparingTo("75.00");
        assertThat(jdbcTemplate.queryForList(
                "SELECT direction FROM transaction_entries "
                        + "WHERE transaction_type = 'TRANSFER' ORDER BY direction",
                String.class))
                .containsExactly("CREDIT", "DEBIT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT completed FROM transfer_idempotency_keys", Boolean.class))
                .isTrue();
    }

    @Test
    void persistenceFailureRollsBackAccountsEntriesAndIdempotencyRecord() {
        createUserWithBalance(ALICE, "100.00");
        createUserWithBalance(BOB, "50.00");
        long aliceVersion = versionOf(ALICE);
        long bobVersion = versionOf(BOB);
        long entriesBefore = countRows("transaction_entries");

        jdbcTemplate.execute("""
                CREATE FUNCTION fail_transfer_credit() RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'simulated ledger failure';
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER fail_transfer_credit
                    BEFORE INSERT ON transaction_entries
                    FOR EACH ROW
                    WHEN (NEW.transaction_type = 'TRANSFER' AND NEW.direction = 'CREDIT')
                    EXECUTE FUNCTION fail_transfer_credit()
                """);
        try {
            assertThatThrownBy(() -> transfer(ALICE, "key-1", BOB, "25.00"))
                    .rootCause()
                    .hasMessageContaining("simulated ledger failure");
        } finally {
            jdbcTemplate.execute("DROP TRIGGER fail_transfer_credit ON transaction_entries");
            jdbcTemplate.execute("DROP FUNCTION fail_transfer_credit()");
        }

        assertThat(balanceOf(ALICE)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(BOB)).isEqualByComparingTo("50.00");
        assertThat(versionOf(ALICE)).isEqualTo(aliceVersion);
        assertThat(versionOf(BOB)).isEqualTo(bobVersion);
        assertThat(countRows("transaction_entries")).isEqualTo(entriesBefore);
        assertThat(countRows("transfer_idempotency_keys")).isZero();
    }

    @Test
    void completedRetryReturnsOriginalResultWithoutSecondLedgerPair() {
        createUserWithBalance(ALICE, "100.00");
        createUserWithBalance(BOB, "50.00");

        TransferResponse first = transfer(ALICE, "key-1", BOB, "25.00");
        TransferResponse retry = transfer(ALICE, "key-1", BOB, "25.00");

        assertThat(retry).isEqualTo(first);
        assertThat(balanceOf(ALICE)).isEqualByComparingTo("75.00");
        assertThat(countTransferEntries()).isEqualTo(2);
    }

    @Test
    void concurrentDuplicateRequestsApplyTransferOnce() throws Exception {
        createUserWithBalance(ALICE, "100.00");
        createUserWithBalance(BOB, "50.00");

        List<Callable<TransferResponse>> requests = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            requests.add(() -> transfer(ALICE, "same-key", BOB, "25.00"));
        }
        List<Future<TransferResponse>> results = runConcurrently(requests);

        TransferResponse expected = results.get(0).get();
        for (Future<TransferResponse> result : results) {
            assertThat(result.get()).isEqualTo(expected);
        }
        assertThat(balanceOf(ALICE)).isEqualByComparingTo("75.00");
        assertThat(balanceOf(BOB)).isEqualByComparingTo("75.00");
        assertThat(countTransferEntries()).isEqualTo(2);
        assertThat(countRows("transfer_idempotency_keys")).isEqualTo(1);
    }

    @Test
    void concurrentTransfersCannotOverspendSender() throws Exception {
        createUserWithBalance(ALICE, "1000.00");
        createUserWithBalance(BOB, "0.00");

        List<Callable<TransferResponse>> requests = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String key = "spend-" + i;
            requests.add(() -> transfer(ALICE, key, BOB, "300.00"));
        }
        List<Future<TransferResponse>> results = runConcurrently(requests);

        int succeeded = 0;
        int insufficient = 0;
        for (Future<TransferResponse> result : results) {
            try {
                result.get();
                succeeded++;
            } catch (ExecutionException exception) {
                assertThat(exception.getCause()).isInstanceOf(InsufficientFundsException.class);
                insufficient++;
            }
        }
        assertThat(succeeded).isEqualTo(3);
        assertThat(insufficient).isEqualTo(2);
        assertThat(balanceOf(ALICE)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(BOB)).isEqualByComparingTo("900.00");
        assertThat(countTransferEntries()).isEqualTo(6);
    }

    @Test
    void oppositeDirectionTransfersCompleteWithoutDeadlock() throws Exception {
        createUserWithBalance(ALICE, "1000.00");
        createUserWithBalance(BOB, "1000.00");

        List<Callable<TransferResponse>> requests = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String aliceKey = "a-to-b-" + i;
            String bobKey = "b-to-a-" + i;
            requests.add(() -> transfer(ALICE, aliceKey, BOB, "10.00"));
            requests.add(() -> transfer(BOB, bobKey, ALICE, "10.00"));
        }
        List<Future<TransferResponse>> results = runConcurrently(requests);

        for (Future<TransferResponse> result : results) {
            result.get();
        }
        assertThat(balanceOf(ALICE)).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(BOB)).isEqualByComparingTo("1000.00");
        assertThat(countTransferEntries()).isEqualTo(80);
    }

    private TransferResponse transfer(
            String senderEmail,
            String idempotencyKey,
            String recipientEmail,
            String amount) {
        return transferService.transfer(
                senderEmail,
                idempotencyKey,
                new TransferRequest(recipientEmail, new BigDecimal(amount))
        );
    }

    private void createUserWithBalance(String email, String balance) {
        User user = userRepository.save(new User(email, email, "hash"));
        Account account = new Account(user);
        BigDecimal amount = new BigDecimal(balance);
        if (amount.signum() > 0) {
            account.credit(amount);
        }
        accountRepository.save(account);
    }

    private <T> List<Future<T>> runConcurrently(List<Callable<T>> tasks)
            throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(tasks.size(), 10));
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
            return futures;
        } finally {
            executor.shutdownNow();
        }
    }

    private BigDecimal balanceOf(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT a.balance FROM accounts a JOIN users u ON u.id = a.user_id "
                        + "WHERE u.email = ?",
                BigDecimal.class,
                email);
    }

    private long versionOf(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT a.version FROM accounts a JOIN users u ON u.id = a.user_id "
                        + "WHERE u.email = ?",
                Long.class,
                email);
    }

    private long countTransferEntries() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM transaction_entries WHERE transaction_type = 'TRANSFER'",
                Long.class);
    }

    private long countRows(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }
}
