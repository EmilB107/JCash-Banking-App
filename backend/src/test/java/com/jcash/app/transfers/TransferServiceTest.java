package com.jcash.app.transfers;

import com.jcash.app.accounts.Account;
import com.jcash.app.accounts.AccountRepository;
import com.jcash.app.transactions.TransactionDirection;
import com.jcash.app.transactions.TransactionEntry;
import com.jcash.app.transactions.TransactionEntryRepository;
import com.jcash.app.transactions.TransactionType;
import com.jcash.app.transfers.dto.TransferRequest;
import com.jcash.app.transfers.dto.TransferResponse;
import com.jcash.app.users.User;
import com.jcash.app.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionEntryRepository transactionEntryRepository;

    @Mock
    private TransferIdempotencyKeyRepository idempotencyRepository;

    @Mock
    private UserRepository userRepository;

    private User senderUser;
    private User recipientUser;
    private Account senderAccount;
    private Account recipientAccount;
    private TransferService transferService;

    @BeforeEach
    void setUp() throws Exception {
        senderUser = new User("sender@example.com", "Sender", "hash");
        recipientUser = new User("recipient@example.com", "Recipient", "hash");
        UUID senderUserId = UUID.randomUUID();
        UUID recipientUserId = UUID.randomUUID();
        setField(senderUser, "id", senderUserId);
        setField(recipientUser, "id", recipientUserId);

        senderAccount = new Account(senderUser);
        recipientAccount = new Account(recipientUser);
        setField(senderAccount, "id", UUID.randomUUID());
        setField(recipientAccount, "id", UUID.randomUUID());
        senderAccount.credit(new BigDecimal("100.00"));
        recipientAccount.credit(new BigDecimal("50.00"));

        lenient().when(userRepository.findByEmail("sender@example.com"))
                .thenReturn(Optional.of(senderUser));
        lenient().when(userRepository.findByEmail("recipient@example.com"))
                .thenReturn(Optional.of(recipientUser));
        lenient().when(accountRepository.findByUserIdForUpdate(senderUserId))
                .thenReturn(Optional.of(senderAccount));
        lenient().when(accountRepository.findByUserIdForUpdate(recipientUserId))
                .thenReturn(Optional.of(recipientAccount));
        lenient().when(idempotencyRepository.saveAndFlush(any(TransferIdempotencyKey.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        transferService = new TransferService(
                accountRepository,
                transactionEntryRepository,
                idempotencyRepository,
                userRepository
        );
    }

    @Test
    void successfulTransferDebitsCreditsAndWritesBothEntries() {
        TransferResponse result = transfer("key-1", "25.00");

        assertThat(result.senderAccountId()).isEqualTo(senderAccount.getId());
        assertThat(result.recipientAccountId()).isEqualTo(recipientAccount.getId());
        assertThat(senderAccount.getBalance()).isEqualByComparingTo("75.00");
        assertThat(recipientAccount.getBalance()).isEqualByComparingTo("75.00");

        ArgumentCaptor<TransactionEntry> entries =
                ArgumentCaptor.forClass(TransactionEntry.class);
        verify(transactionEntryRepository, org.mockito.Mockito.times(2)).save(entries.capture());
        assertThat(entries.getAllValues())
                .extracting(TransactionEntry::getAccount)
                .containsExactly(senderAccount, recipientAccount);
        assertThat(entries.getAllValues())
                .extracting(TransactionEntry::getDirection)
                .containsExactly(TransactionDirection.DEBIT, TransactionDirection.CREDIT);
        assertThat(entries.getAllValues())
                .allSatisfy(entry -> {
                    assertThat(entry.getTransactionType()).isEqualTo(TransactionType.TRANSFER);
                    assertThat(entry.getAmount()).isEqualByComparingTo("25.00");
                });
    }

    @Test
    void locksAccountsInAscendingUserIdOrderForBothDirections() {
        UUID lowerUserId = senderUser.getId().compareTo(recipientUser.getId()) < 0
                ? senderUser.getId()
                : recipientUser.getId();
        UUID higherUserId = lowerUserId.equals(senderUser.getId())
                ? recipientUser.getId()
                : senderUser.getId();

        transfer("key-1", "25.00");
        transferService.transfer(
                "recipient@example.com",
                "key-2",
                new TransferRequest("sender@example.com", new BigDecimal("10.00"))
        );

        InOrder lockOrder = inOrder(accountRepository);
        lockOrder.verify(accountRepository).findByUserIdForUpdate(lowerUserId);
        lockOrder.verify(accountRepository).findByUserIdForUpdate(higherUserId);
        lockOrder.verify(accountRepository).findByUserIdForUpdate(lowerUserId);
        lockOrder.verify(accountRepository).findByUserIdForUpdate(higherUserId);
    }

    @Test
    void mixedCaseRecipientEmailResolvesToStoredRecipient() {
        TransferResponse result = transferTo("key-1", "  Recipient@Example.COM ", "25.00");

        assertThat(result.recipientAccountId()).isEqualTo(recipientAccount.getId());
        assertThat(result.recipientEmail()).isEqualTo("recipient@example.com");
        verify(userRepository).findByEmail("recipient@example.com");

        ArgumentCaptor<TransferIdempotencyKey> reservation =
                ArgumentCaptor.forClass(TransferIdempotencyKey.class);
        verify(idempotencyRepository).saveAndFlush(reservation.capture());
        assertThat(reservation.getValue().getRecipientEmail()).isEqualTo("recipient@example.com");
    }

    @Test
    void completedDuplicateReturnsOriginalResultWithoutWritingAgain() {
        TransferIdempotencyKey existing = new TransferIdempotencyKey(
                senderAccount, "key-1", "recipient@example.com", new BigDecimal("25.00"));
        existing.markCompleted(recipientAccount, new BigDecimal("75.00"),
                new BigDecimal("75.00"), "PHP");
        when(idempotencyRepository.findBySenderAccountIdAndIdempotencyKey(
                senderAccount.getId(), "key-1"))
                .thenReturn(Optional.of(existing));

        TransferResponse result = transfer("key-1", "25.00");

        assertThat(result.senderBalanceAfter()).isEqualByComparingTo("75.00");
        verify(idempotencyRepository, never()).saveAndFlush(any());
        verifyNoInteractions(transactionEntryRepository);
    }

    @Test
    void completedRetryWithDifferentRecipientCaseReturnsOriginalResult() {
        TransferIdempotencyKey existing = new TransferIdempotencyKey(
                senderAccount, "key-1", "recipient@example.com", new BigDecimal("25.00"));
        existing.markCompleted(recipientAccount, new BigDecimal("75.00"),
                new BigDecimal("75.00"), "PHP");
        when(idempotencyRepository.findBySenderAccountIdAndIdempotencyKey(
                senderAccount.getId(), "key-1"))
                .thenReturn(Optional.of(existing));

        TransferResponse result = transferTo("key-1", "RECIPIENT@example.com", "25.00");

        assertThat(result).isEqualTo(existing.toResponse());
        verify(idempotencyRepository, never()).saveAndFlush(any());
        verifyNoInteractions(transactionEntryRepository);
    }

    @Test
    void conflictingDuplicateIsRejected() {
        TransferIdempotencyKey existing = new TransferIdempotencyKey(
                senderAccount, "key-1", "recipient@example.com", new BigDecimal("25.00"));
        when(idempotencyRepository.findBySenderAccountIdAndIdempotencyKey(
                senderAccount.getId(), "key-1"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transfer("key-1", "30.00"))
                .isInstanceOf(IdempotencyKeyConflictException.class);
        verify(idempotencyRepository, never()).saveAndFlush(any());
    }

    @Test
    void inProgressDuplicateIsRejected() {
        TransferIdempotencyKey existing = new TransferIdempotencyKey(
                senderAccount, "key-1", "recipient@example.com", new BigDecimal("25.00"));
        when(idempotencyRepository.findBySenderAccountIdAndIdempotencyKey(
                senderAccount.getId(), "key-1"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transfer("key-1", "25.00"))
                .isInstanceOf(IdempotencyKeyInProgressException.class);
    }

    @Test
    void rejectsMissingRecipient() {
        when(userRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferTo("key-1", "missing@example.com", "25.00"))
                .isInstanceOf(RecipientNotFoundException.class);
    }

    @Test
    void rejectsSelfTransferBeforeLockingOrWriting() {
        when(userRepository.findByEmail("recipient@example.com"))
                .thenReturn(Optional.of(senderUser));

        assertThatThrownBy(() -> transfer("key-1", "25.00"))
                .isInstanceOf(SelfTransferNotAllowedException.class);
        assertThat(senderAccount.getBalance()).isEqualByComparingTo("100.00");
        verifyNoInteractions(accountRepository, idempotencyRepository, transactionEntryRepository);
    }

    @Test
    void rejectsInsufficientFundsBeforeWritingEntries() {
        assertThatThrownBy(() -> transfer("key-1", "125.00"))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(senderAccount.getBalance()).isEqualByComparingTo("100.00");
        verify(transactionEntryRepository, never()).save(any());
    }

    @Test
    void rejectsInvalidIdempotencyKeysBeforeRepositoryAccess() {
        for (String key : new String[] {null, "", "   ", "x".repeat(256)}) {
            assertThatThrownBy(() -> transfer(key, "25.00"))
                    .isInstanceOf(InvalidIdempotencyKeyException.class);
        }
        verifyNoInteractions(userRepository, accountRepository, idempotencyRepository);
    }

    @Test
    void rejectsInvalidAmountsBeforeRepositoryAccess() {
        for (BigDecimal amount : new BigDecimal[] {
                null, BigDecimal.ZERO, new BigDecimal("-1.00"), new BigDecimal("1.001")
        }) {
            assertThatThrownBy(() -> transfer("key-1", amount))
                    .isInstanceOf(InvalidTransferAmountException.class);
        }
        verifyNoInteractions(userRepository, accountRepository, idempotencyRepository);
    }

    @Test
    void propagatesLedgerWriteFailureForTransactionRollback() {
        doThrow(new IllegalStateException("entry write failed"))
                .when(transactionEntryRepository).save(any());

        assertThatThrownBy(() -> transfer("key-1", "25.00"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("entry write failed");
        verify(idempotencyRepository).saveAndFlush(any(TransferIdempotencyKey.class));
    }

    @Test
    void propagatesOptimisticLockConflict() {
        doThrow(new ObjectOptimisticLockingFailureException(
                Account.class, senderAccount.getId()))
                .when(accountRepository).flush();

        assertThatThrownBy(() -> transfer("key-1", "25.00"))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    private TransferResponse transfer(String key, String amount) {
        return transferTo(key, "recipient@example.com", amount);
    }

    private TransferResponse transfer(String key, BigDecimal amount) {
        return transferTo(key, "recipient@example.com", amount);
    }

    private TransferResponse transferTo(String key, String recipientEmail, String amount) {
        return transferTo(key, recipientEmail, new BigDecimal(amount));
    }

    private TransferResponse transferTo(String key, String recipientEmail, BigDecimal amount) {
        return transferService.transfer(
                "sender@example.com",
                key,
                new TransferRequest(recipientEmail, amount)
        );
    }

    private static void setField(Object target, String fieldName, Object value)
            throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
