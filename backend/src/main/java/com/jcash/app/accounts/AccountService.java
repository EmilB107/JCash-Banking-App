package com.jcash.app.accounts;

import com.jcash.app.users.User;
import com.jcash.app.users.UserNotFoundException;
import com.jcash.app.users.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountService(
            AccountRepository accountRepository,
            UserRepository userRepository) {
        this.accountRepository = accountRepository;
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
        return account;
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));
    }
}
