package com.jcash.app.users;

import com.jcash.app.accounts.Account;
import com.jcash.app.accounts.AccountRepository;
import com.jcash.app.users.exception.UserAlreadyExistsException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.SQLException;

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
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService.createUser(
                "demo@example.com",
                "Demo User",
                "password123"
        );

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
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
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void createUserStoresTrimmedLowercaseEmail() {
        when(userRepository.existsByEmail("demo@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("bcrypt-hash");
        when(userRepository.saveAndFlush(any(User.class)))
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
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void createUserTranslatesConcurrentDuplicateEmailViolation() {
        when(userRepository.existsByEmail("demo@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("bcrypt-hash");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(integrityViolation(UserService.EMAIL_UNIQUE_CONSTRAINT));

        assertThatThrownBy(() -> userService.createUser(
                "demo@example.com",
                "Demo User",
                "password123"
        ))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("demo@example.com");

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void createUserRethrowsUnrelatedIntegrityViolation() {
        DataIntegrityViolationException unrelated =
                integrityViolation("chk_users_email_normalized");
        when(userRepository.existsByEmail("demo@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("bcrypt-hash");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(unrelated);

        assertThatThrownBy(() -> userService.createUser(
                "demo@example.com",
                "Demo User",
                "password123"
        ))
                .isSameAs(unrelated);

        verify(accountRepository, never()).save(any(Account.class));
    }

    private static DataIntegrityViolationException integrityViolation(String constraintName) {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new ConstraintViolationException(
                        "constraint violated",
                        new SQLException("constraint violated", "23505"),
                        constraintName
                )
        );
    }
}
