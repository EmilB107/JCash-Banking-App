package com.jcash.app.accounts;

import com.jcash.app.transactions.TransactionEntryRepository;
import com.jcash.app.users.User;
import com.jcash.app.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceRollbackTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionEntryRepository transactionEntryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private User user;

    @Test
    void cashInFailureIsPropagatedForTransactionRollback()
            throws NoSuchMethodException {
        AccountService accountService = new AccountService(
                accountRepository,
                transactionEntryRepository,
                userRepository
        );
        UUID userId = UUID.randomUUID();
        Account account = new Account(user);
        when(userRepository.findByEmail("demo@example.com"))
                .thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(accountRepository.findByUserId(userId))
                .thenReturn(Optional.of(account));
        doThrow(new IllegalStateException("entry write failed"))
                .when(transactionEntryRepository)
                .save(org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> accountService.cashIn(
                "demo@example.com",
                new BigDecimal("25.00")
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("entry write failed");
        assertThat(AccountService.class
                .getDeclaredMethod("cashIn", String.class, BigDecimal.class)
                .isAnnotationPresent(Transactional.class))
                .isTrue();
    }
}
