package com.jcash.app.transactions;

import com.jcash.app.accounts.AccountService;
import com.jcash.app.common.FreshTestDatabaseConfiguration;
import com.jcash.app.transactions.dto.TransactionEntryResponse;
import com.jcash.app.transfers.TransferService;
import com.jcash.app.transfers.dto.TransferRequest;
import com.jcash.app.users.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        FreshTestDatabaseConfiguration.DATASOURCE_URL,
        FreshTestDatabaseConfiguration.CLEAN_ENABLED
})
@Import(FreshTestDatabaseConfiguration.class)
class TransactionHistoryIntegrationTest {

    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";
    private static final String ALICE_MOBILE = "09170000001";
    private static final String BOB_MOBILE = "09170000002";

    @Autowired
    private UserService userService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private TransactionHistoryService transactionHistoryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM transfer_idempotency_keys");
        jdbcTemplate.update("DELETE FROM transaction_entries");
        jdbcTemplate.update("DELETE FROM accounts");
        jdbcTemplate.update("DELETE FROM users");

        userService.createUser(ALICE, ALICE_MOBILE, "Alice Cruz", "123456");
        userService.createUser(BOB, BOB_MOBILE, "Bob Santos", "123456");
    }

    @Test
    void historyShowsCashInAndBothSidesOfALinkedTransfer() {
        accountService.cashIn(ALICE, new BigDecimal("100.00"));
        transfer(ALICE, "key-1", BOB_MOBILE, "25.00");

        List<TransactionEntryResponse> aliceHistory = historyOf(ALICE);
        List<TransactionEntryResponse> bobHistory = historyOf(BOB);

        assertThat(aliceHistory).hasSize(2);
        TransactionEntryResponse aliceTransfer = aliceHistory.get(0);
        TransactionEntryResponse aliceCashIn = aliceHistory.get(1);
        assertThat(aliceTransfer.direction()).isEqualTo(TransactionDirection.DEBIT);
        assertThat(aliceTransfer.details()).isEqualTo("Transfer to Bob Santos (09170000002)");
        assertThat(aliceCashIn.transactionType()).isEqualTo(TransactionType.CASH_IN);
        assertThat(aliceCashIn.details()).isEqualTo("Cash in");
        assertThat(aliceCashIn.transferReference()).isNull();

        TransactionEntryResponse bobTransfer = bobHistory.get(0);
        assertThat(bobHistory).hasSize(1);
        assertThat(bobTransfer.direction()).isEqualTo(TransactionDirection.CREDIT);
        assertThat(bobTransfer.details()).isEqualTo("Transfer from Alice Cruz (09170000001)");
        assertThat(bobTransfer.transferReference())
                .isNotNull()
                .isEqualTo(aliceTransfer.transferReference());

        assertThat(counterpartyEmailOf(aliceTransfer.id())).isEqualTo(BOB);
        assertThat(counterpartyEmailOf(bobTransfer.id())).isEqualTo(ALICE);
    }

    @Test
    void idempotentRetryDoesNotAddEntriesOrReferences() {
        accountService.cashIn(ALICE, new BigDecimal("100.00"));
        transfer(ALICE, "key-1", BOB, "25.00");
        transfer(ALICE, "key-1", BOB_MOBILE, "25.00");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT transfer_reference) FROM transaction_entries",
                Long.class))
                .isEqualTo(1);
        assertThat(historyOf(BOB)).hasSize(1);
    }

    @Test
    void detailsAreASnapshotThatSurvivesLaterNameChanges() {
        accountService.cashIn(ALICE, new BigDecimal("100.00"));
        transfer(ALICE, "key-1", BOB, "25.00");

        jdbcTemplate.update("UPDATE users SET display_name = 'Robert Santos' WHERE email = ?", BOB);

        assertThat(historyOf(ALICE).get(0).details())
                .isEqualTo("Transfer to Bob Santos (09170000002)");
    }

    @Test
    void databaseRejectsInconsistentTransferLinks() {
        UUID aliceAccount = accountIdOf(ALICE);
        UUID bobAccount = accountIdOf(BOB);
        UUID reference = UUID.randomUUID();

        // A transfer entry must name its counterparty and reference.
        assertThatThrownBy(() -> insertEntry(aliceAccount, "TRANSFER", "DEBIT", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertEntry(aliceAccount, "TRANSFER", "DEBIT", bobAccount, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        // A cash-in entry must not.
        assertThatThrownBy(() ->
                insertEntry(aliceAccount, "CASH_IN", "CREDIT", bobAccount, reference))
                .isInstanceOf(DataIntegrityViolationException.class);

        // A reference links at most one DEBIT and one CREDIT.
        insertEntry(aliceAccount, "TRANSFER", "DEBIT", bobAccount, reference);
        insertEntry(bobAccount, "TRANSFER", "CREDIT", aliceAccount, reference);
        assertThatThrownBy(() ->
                insertEntry(aliceAccount, "TRANSFER", "DEBIT", bobAccount, reference))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsBlankDetails() {
        UUID aliceAccount = accountIdOf(ALICE);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO transaction_entries
                    (id, account_id, transaction_type, direction, amount, currency,
                     balance_after, details)
                VALUES (?, ?, 'CASH_IN', 'CREDIT', 1.00, 'PHP', 1.00, '   ')
                """, UUID.randomUUID(), aliceAccount))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void transfer(String sender, String key, String recipient, String amount) {
        transferService.transfer(sender, key, new TransferRequest(recipient, new BigDecimal(amount)));
    }

    private List<TransactionEntryResponse> historyOf(String email) {
        return transactionHistoryService.findAuthenticatedUserHistory(email);
    }

    private void insertEntry(
            UUID accountId,
            String type,
            String direction,
            UUID counterpartyAccountId,
            UUID transferReference) {
        jdbcTemplate.update("""
                INSERT INTO transaction_entries
                    (id, account_id, transaction_type, direction, amount, currency,
                     balance_after, details, counterparty_account_id, transfer_reference)
                VALUES (?, ?, ?, ?, 1.00, 'PHP', 1.00, 'test entry', ?, ?)
                """,
                UUID.randomUUID(), accountId, type, direction,
                counterpartyAccountId, transferReference);
    }

    private UUID accountIdOf(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT a.id FROM accounts a JOIN users u ON u.id = a.user_id WHERE u.email = ?",
                UUID.class, email);
    }

    private String counterpartyEmailOf(UUID entryId) {
        return jdbcTemplate.queryForObject("""
                SELECT u.email
                FROM transaction_entries e
                JOIN accounts a ON a.id = e.counterparty_account_id
                JOIN users u ON u.id = a.user_id
                WHERE e.id = ?
                """, String.class, entryId);
    }
}
