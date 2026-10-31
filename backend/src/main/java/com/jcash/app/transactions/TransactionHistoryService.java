package com.jcash.app.transactions;

import com.jcash.app.accounts.AccountRepository;
import com.jcash.app.transactions.dto.TransactionEntryResponse;
import com.jcash.app.transactions.dto.TransactionHistoryPage;
import com.jcash.app.transactions.exception.InvalidPageRequestException;
import com.jcash.app.users.exception.UserNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TransactionHistoryService {

    public static final int MAX_PAGE_SIZE = 100;

    // Newest first; id breaks ties so entries never shift between pages.
    private static final Sort NEWEST_FIRST = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );

    private final TransactionEntryRepository transactionEntryRepository;
    private final AccountRepository accountRepository;

    public TransactionHistoryService(
            TransactionEntryRepository transactionEntryRepository,
            AccountRepository accountRepository) {
        this.transactionEntryRepository = transactionEntryRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public TransactionHistoryPage findAuthenticatedUserHistory(
            String email, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidPageRequestException(MAX_PAGE_SIZE);
        }

        // Every user gets an account at registration, so no account id means
        // no user. Filtering entries by account id (not by a join on the
        // user) lets PostgreSQL read just one page from the history index.
        UUID accountId = accountRepository.findIdByUserEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        return TransactionHistoryPage.from(transactionEntryRepository
                .findByAccountId(
                        accountId,
                        PageRequest.of(page, size, NEWEST_FIRST)
                )
                .map(TransactionEntryResponse::from));
    }
}
