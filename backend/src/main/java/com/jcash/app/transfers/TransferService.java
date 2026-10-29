package com.jcash.app.transfers;

import com.jcash.app.accounts.Account;
import com.jcash.app.accounts.AccountRepository;
import com.jcash.app.accounts.exception.AccountNotFoundException;
import com.jcash.app.transactions.TransactionDirection;
import com.jcash.app.transactions.TransactionEntry;
import com.jcash.app.transactions.TransactionEntryRepository;
import com.jcash.app.transactions.TransactionType;
import com.jcash.app.transfers.dto.TransferRequest;
import com.jcash.app.transfers.dto.TransferResponse;
import com.jcash.app.transfers.exception.IdempotencyKeyConflictException;
import com.jcash.app.transfers.exception.IdempotencyKeyInProgressException;
import com.jcash.app.transfers.exception.InsufficientFundsException;
import com.jcash.app.transfers.exception.InvalidIdempotencyKeyException;
import com.jcash.app.transfers.exception.InvalidTransferAmountException;
import com.jcash.app.transfers.exception.RecipientNotFoundException;
import com.jcash.app.transfers.exception.SelfTransferNotAllowedException;
import com.jcash.app.users.EmailNormalizer;
import com.jcash.app.users.User;
import com.jcash.app.users.UserRepository;
import com.jcash.app.users.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class TransferService {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 255;
    private static final int MONETARY_SCALE = 2;

    private final AccountRepository accountRepository;
    private final TransactionEntryRepository transactionEntryRepository;
    private final TransferIdempotencyKeyRepository transferIdempotencyKeyRepository;
    private final UserRepository userRepository;

    public TransferService(
            AccountRepository accountRepository,
            TransactionEntryRepository transactionEntryRepository,
            TransferIdempotencyKeyRepository transferIdempotencyKeyRepository,
            UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.transactionEntryRepository = transactionEntryRepository;
        this.transferIdempotencyKeyRepository = transferIdempotencyKeyRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TransferResponse transfer(
            String senderEmail,
            String idempotencyKey,
            TransferRequest request) {

        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        String recipientEmail = normalizeRecipientEmail(
                request == null ? null : request.recipientEmail()
        );
        BigDecimal amount = validateTransferAmount(
                request == null ? null : request.amount()
        );

        User sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new UserNotFoundException(senderEmail));
        User recipient = userRepository.findByEmail(recipientEmail)
                .orElseThrow(() -> new RecipientNotFoundException(recipientEmail));
        if (sender.getId().equals(recipient.getId())) {
            throw new SelfTransferNotAllowedException();
        }

        // Lock both account rows in a consistent order so that opposite-direction
        // transfers (A->B and B->A) wait on each other instead of deadlocking.
        Account senderAccount;
        Account recipientAccount;
        if (sender.getId().compareTo(recipient.getId()) < 0) {
            senderAccount = lockSenderAccount(sender);
            recipientAccount = lockRecipientAccount(recipient, recipientEmail);
        } else {
            recipientAccount = lockRecipientAccount(recipient, recipientEmail);
            senderAccount = lockSenderAccount(sender);
        }

        TransferResponse existingResult = findExistingResult(
                senderAccount.getId(),
                normalizedIdempotencyKey,
                recipientEmail,
                amount
        );
        if (existingResult != null) {
            return existingResult;
        }

        TransferIdempotencyKey idempotencyReservation =
                transferIdempotencyKeyRepository.saveAndFlush(
                        new TransferIdempotencyKey(
                                senderAccount,
                                normalizedIdempotencyKey,
                                recipientEmail,
                                amount
                        )
                );

        try {
            senderAccount.debit(amount);
        } catch (IllegalStateException exception) {
            throw new InsufficientFundsException();
        }
        recipientAccount.credit(amount);

        transactionEntryRepository.save(new TransactionEntry(
                senderAccount,
                TransactionType.TRANSFER,
                TransactionDirection.DEBIT,
                amount,
                senderAccount.getCurrency(),
                senderAccount.getBalance()
        ));
        transactionEntryRepository.save(new TransactionEntry(
                recipientAccount,
                TransactionType.TRANSFER,
                TransactionDirection.CREDIT,
                amount,
                recipientAccount.getCurrency(),
                recipientAccount.getBalance()
        ));

        idempotencyReservation.markCompleted(
                recipientAccount,
                senderAccount.getBalance(),
                recipientAccount.getBalance(),
                senderAccount.getCurrency()
        );

        accountRepository.flush();

        return idempotencyReservation.toResponse();
    }

    private Account lockSenderAccount(User sender) {
        return accountRepository.findByUserIdForUpdate(sender.getId())
                .orElseThrow(() -> new AccountNotFoundException(sender.getId()));
    }

    private Account lockRecipientAccount(User recipient, String recipientEmail) {
        return accountRepository.findByUserIdForUpdate(recipient.getId())
                .orElseThrow(() -> new RecipientNotFoundException(recipientEmail));
    }

    private TransferResponse findExistingResult(
            UUID senderAccountId,
            String idempotencyKey,
            String recipientEmail,
            BigDecimal amount) {
        return transferIdempotencyKeyRepository
                .findBySenderAccountIdAndIdempotencyKey(senderAccountId, idempotencyKey)
                .map(existing -> resolveExisting(existing, recipientEmail, amount))
                .orElse(null);
    }

    private TransferResponse resolveExisting(
            TransferIdempotencyKey existing,
            String recipientEmail,
            BigDecimal amount) {
        boolean sameRecipient = existing.getRecipientEmail().equalsIgnoreCase(recipientEmail);
        boolean sameAmount = existing.getAmount().compareTo(amount) == 0;

        if (!sameRecipient || !sameAmount) {
            throw new IdempotencyKeyConflictException();
        }

        if (!existing.isCompleted()) {
            throw new IdempotencyKeyInProgressException();
        }

        return existing.toResponse();
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            throw new InvalidIdempotencyKeyException();
        }

        String normalized = idempotencyKey.trim();
        if (normalized.isEmpty() || normalized.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new InvalidIdempotencyKeyException();
        }

        return normalized;
    }

    private String normalizeRecipientEmail(String recipientEmail) {
        if (recipientEmail == null) {
            throw new RecipientNotFoundException("null");
        }

        String normalized = EmailNormalizer.normalize(recipientEmail);
        if (normalized.isEmpty()) {
            throw new RecipientNotFoundException(recipientEmail);
        }

        return normalized;
    }

    private BigDecimal validateTransferAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > MONETARY_SCALE
                || amount.precision() - amount.scale() > 17) {
            throw new InvalidTransferAmountException();
        }

        return amount;
    }
}
