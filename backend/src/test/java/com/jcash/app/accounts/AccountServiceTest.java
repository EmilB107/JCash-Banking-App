package com.jcash.app.accounts;

import com.jcash.app.users.User;
import com.jcash.app.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private User user;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, userRepository);
    }

    @Test
    void newAccountStartsInPhpWithZeroBalance() {
        Account account = new Account(user);

        assertThat(account.getCurrency()).isEqualTo(Account.DEFAULT_CURRENCY);
        assertThat(account.getBalance()).isZero();
    }

    @Test
    void findsTheAccountForTheAuthenticatedUser() {
        UUID userId = UUID.randomUUID();
        Account account = new Account(user);
        when(userRepository.findByEmail("demo@example.com"))
                .thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(accountRepository.findByUserId(userId))
                .thenReturn(Optional.of(account));

        Account result = accountService.findAuthenticatedUserAccount(
                "demo@example.com"
        );

        assertThat(result).isSameAs(account);
        verify(userRepository).findByEmail("demo@example.com");
        verify(accountRepository).findByUserId(userId);
    }

    @Test
    void cashInIncreasesTheAuthenticatedUsersBalance() {
        UUID userId = UUID.randomUUID();
        Account account = new Account(user);
        when(userRepository.findByEmail("demo@example.com"))
                .thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(accountRepository.findByUserId(userId))
                .thenReturn(Optional.of(account));

        Account result = accountService.cashIn(
                "demo@example.com",
                new BigDecimal("125.50")
        );

        assertThat(result.getBalance()).isEqualByComparingTo("125.50");
    }

    @Test
    void rejectsInvalidCashInAmountsBeforeLoadingAnAccount() {
        for (BigDecimal amount : new BigDecimal[] {
                null,
                BigDecimal.ZERO,
                new BigDecimal("-1.00")
        }) {
            assertThatThrownBy(() ->
                    accountService.cashIn("demo@example.com", amount)
            )
                    .isInstanceOf(InvalidCashInAmountException.class)
                    .hasMessage("Cash-in amount must be greater than zero");
        }

        verifyNoInteractions(userRepository, accountRepository);
    }

    @Test
    void accountLookupUsesAuthenticatedUserIdentityOnly() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findByEmail("demo@example.com"))
                .thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(accountRepository.findByUserId(userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                accountService.findAuthenticatedUserAccount("demo@example.com")
        )
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(userId.toString());

        verify(accountRepository).findByUserId(userId);
    }

    @Test
    void reportsMissingUser() {
        when(userRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                accountService.findAuthenticatedUserAccount("missing@example.com")
        )
                .isInstanceOf(com.jcash.app.users.UserNotFoundException.class)
                .hasMessageContaining("missing@example.com");
    }
}
