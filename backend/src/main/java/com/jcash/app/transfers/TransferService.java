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
import com.jcash.app.users.User;
import com.jcash.app.users.UserLookupService;
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
    private final UserLookupService userLookupService;

    public TransferService(
            AccountRepository accountRepository,
            TransactionEntryRepository transactionEntryRepository,
            TransferIdempotencyKeyRepository transferIdempotencyKeyRepository,
            UserRepository userRepository,
            UserLookupService userLookupService) {
        this.accountRepository = accountRepository;
        this.transactionEntryRepository = transactionEntryRepository;
        this.transferIdempotencyKeyRepository = transferIdempotencyKeyRepository;
        this.userRepository = userRepository;
        this.userLookupService = userLookupService;
    }

    @Transactional
    public TransferResponse transfer(
            String senderEmail,
            String idempotencyKey,
            TransferRequest request) {

        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        String recipientIdentifier = requireRecipient(
                request == null ? null : request.recipient()
        );
        BigDecimal amount = validateTransferAmount(
                request == null ? null : request.amount()
        );

        User sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new UserNotFoundException(senderEmail));
        User recipient = userLookupService.findByIdentifier(recipientIdentifier)
                .orElseThrow(() -> new RecipientNotFoundException(recipientIdentifier));
        // The idempotency record keys on the resolved email, so a retry that names the
        // same recipient by mobile number instead of email is the same request.
        String recipientEmail = recipient.getEmail();
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

        UUID transferReference = UUID.randomUUID();
        transactionEntryRepository.save(new TransactionEntry(
                senderAccount,
                TransactionType.TRANSFER,
                TransactionDirection.DEBIT,
                amount,
                senderAccount.getCurrency(),
                senderAccount.getBalance(),
                transferDetails("Transfer to ", recipient),
                recipientAccount,
                transferReference
        ));
        transactionEntryRepository.save(new TransactionEntry(
                recipientAccount,
                TransactionType.TRANSFER,
                TransactionDirection.CREDIT,
                amount,
                recipientAccount.getCurrency(),
                recipientAccount.getBalance(),
                transferDetails("Transfer from ", sender),
                senderAccount,
                transferReference
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

    // Details are a snapshot: later name changes do not rewrite history.
    private static String transferDetails(String prefix, User counterparty) {
        return prefix + counterparty.getDisplayName()
                + " (" + counterparty.getMobileNumber() + ")";
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

    private String requireRecipient(String recipient) {
        if (recipient == null) {
            throw new RecipientNotFoundException("null");
        }

        String trimmed = recipient.trim();
        if (trimmed.isEmpty()) {
            throw new RecipientNotFoundException(recipient);
        }

        return trimmed;
    }

    private BigDecimal validateTransferAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > MONETARY_SCALE
                || amount.precision() - amount.scale() > 17) {
            throw new InvalidTransferAmountException();
        }

        return amount;
    }
}
