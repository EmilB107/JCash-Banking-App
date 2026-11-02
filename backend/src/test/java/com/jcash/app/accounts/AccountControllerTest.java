package com.jcash.app.accounts;

import com.jcash.app.accounts.dto.AccountResponse;
import com.jcash.app.users.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private AccountService accountService;

    @Mock
    private Authentication authentication;

    @Test
    void currentAccountReturnsOnlySafeAccountFields() {
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.parse("2025-01-01T00:00:00Z");
        User user = new User("demo@example.com", "09171234567", "Demo User", "hashed");
        Account account = new Account(user);

        org.springframework.test.util.ReflectionTestUtils.setField(account, "id", accountId);
        org.springframework.test.util.ReflectionTestUtils.setField(account, "balance", new BigDecimal("250.00"));
        org.springframework.test.util.ReflectionTestUtils.setField(account, "createdAt", now);
        org.springframework.test.util.ReflectionTestUtils.setField(account, "updatedAt", now);

        when(authentication.getName()).thenReturn("demo@example.com");
        when(accountService.findAuthenticatedUserAccount("demo@example.com"))
                .thenReturn(account);

        AccountController controller = new AccountController(accountService);
        AccountResponse response = controller.currentAccount(authentication);

        assertThat(response.id()).isEqualTo(accountId);
        assertThat(response.currency()).isEqualTo("PHP");
        assertThat(response.balance()).isEqualByComparingTo("250.00");
        assertThat(response.createdAt()).isEqualTo(now);
        assertThat(response.updatedAt()).isEqualTo(now);
        assertThat(response).hasNoNullFieldsOrProperties();
    }
}
