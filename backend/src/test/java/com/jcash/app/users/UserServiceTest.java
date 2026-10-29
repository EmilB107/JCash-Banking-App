package com.jcash.app.users;

import com.jcash.app.accounts.Account;
import com.jcash.app.accounts.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                accountRepository,
                passwordEncoder
        );
    }

    @Test
    void createUserHashesPasswordAndCreatesPhpAccount() {
        when(userRepository.existsByEmail("demo@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService.createUser(
                "demo@example.com",
                "Demo User",
                "password123"
        );

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        verify(passwordEncoder).encode("password123");

        assertThat(user).isSameAs(userCaptor.getValue());
        assertThat(user.getPasswordHash()).isEqualTo("bcrypt-hash");

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());

        assertThat(accountCaptor.getValue().getUser()).isSameAs(user);
        assertThat(accountCaptor.getValue().getCurrency())
                .isEqualTo(Account.DEFAULT_CURRENCY);
        assertThat(accountCaptor.getValue().getBalance())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void createUserRejectsDuplicateEmailBeforeHashingOrSaving() {
        when(userRepository.existsByEmail("demo@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(
                "demo@example.com",
                "Demo User",
                "password123"
        ))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("demo@example.com");

        verify(passwordEncoder, never()).encode(any(String.class));
        verify(userRepository, never()).save(any(User.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void createUserStoresTrimmedLowercaseEmail() {
        when(userRepository.existsByEmail("demo@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService.createUser(
                "  Demo@Example.COM ",
                "Demo User",
                "password123"
        );

        assertThat(user.getEmail()).isEqualTo("demo@example.com");
        verify(userRepository).existsByEmail("demo@example.com");
    }

    @Test
    void createUserRejectsDuplicateEmailRegardlessOfCase() {
        when(userRepository.existsByEmail("demo@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(
                "DEMO@example.com",
                "Demo User",
                "password123"
        ))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("demo@example.com");

        verify(passwordEncoder, never()).encode(any(String.class));
        verify(userRepository, never()).save(any(User.class));
    }
}
