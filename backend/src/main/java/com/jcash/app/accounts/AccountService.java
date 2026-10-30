package com.jcash.app.accounts;

import com.jcash.app.accounts.exception.AccountNotFoundException;
import com.jcash.app.accounts.exception.InvalidCashInAmountException;
import com.jcash.app.transactions.TransactionDirection;
import com.jcash.app.transactions.TransactionEntry;
import com.jcash.app.transactions.TransactionEntryRepository;
import com.jcash.app.transactions.TransactionType;
import com.jcash.app.users.User;
import com.jcash.app.users.UserRepository;
import com.jcash.app.users.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AccountService {

    static final String CASH_IN_DETAILS = "Cash in";

    private final AccountRepository accountRepository;
    private final TransactionEntryRepository transactionEntryRepository;
    private final UserRepository userRepository;

    public AccountService(
            AccountRepository accountRepository,
            TransactionEntryRepository transactionEntryRepository,
            UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.transactionEntryRepository = transactionEntryRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Account findAuthenticatedUserAccount(String email) {
        User user = findUser(email);

        return accountRepository.findByUserId(user.getId())
                .orElseThrow(() -> new AccountNotFoundException(user.getId()));
    }

    @Transactional
    public Account cashIn(String email, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidCashInAmountException();
        }

        Account account = findAuthenticatedUserAccount(email);
        account.credit(amount);
        transactionEntryRepository.save(new TransactionEntry(
                account,
                TransactionType.CASH_IN,
                TransactionDirection.CREDIT,
                amount,
                account.getCurrency(),
                account.getBalance(),
                CASH_IN_DETAILS
        ));
        return account;
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));
    }
}
