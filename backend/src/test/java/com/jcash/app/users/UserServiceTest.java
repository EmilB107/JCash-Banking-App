package com.jcash.app.users;

import com.jcash.app.accounts.Account;
import com.jcash.app.accounts.AccountRepository;
import com.jcash.app.users.exception.MobileNumberAlreadyRegisteredException;
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

    private static final String EMAIL = "demo@example.com";
    private static final String MOBILE = "09171234567";
    private static final String PIN = "123456";

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
    void createUserHashesPinAndCreatesPhpAccount() {
        stubSuccessfulSave();

        User user = userService.createUser(EMAIL, MOBILE, "Demo User", PIN);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        verify(passwordEncoder).encode(PIN);

        assertThat(user).isSameAs(userCaptor.getValue());
        assertThat(user.getPinHash()).isEqualTo("bcrypt-hash");
        assertThat(user.getMobileNumber()).isEqualTo(MOBILE);
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();

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
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(EMAIL, MOBILE, "Demo User", PIN))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining(EMAIL);

        verify(passwordEncoder, never()).encode(any(String.class));
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void createUserStoresTrimmedLowercaseEmail() {
        stubSuccessfulSave();

        User user = userService.createUser("  Demo@Example.COM ", MOBILE, "Demo User", PIN);

        assertThat(user.getEmail()).isEqualTo(EMAIL);
        verify(userRepository).existsByEmail(EMAIL);
    }

    @Test
    void createUserRejectsDuplicateEmailRegardlessOfCase() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(
                "DEMO@example.com", MOBILE, "Demo User", PIN))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining(EMAIL);

        verify(passwordEncoder, never()).encode(any(String.class));
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void createUserStoresInternationalMobileNumberInCanonicalForm() {
        stubSuccessfulSave();

        User user = userService.createUser(EMAIL, " +639171234567 ", "Demo User", PIN);

        assertThat(user.getMobileNumber()).isEqualTo(MOBILE);
        verify(userRepository).existsByMobileNumber(MOBILE);
    }

    @Test
    void createUserRejectsDuplicateMobileNumberInEitherFormat() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(userRepository.existsByMobileNumber(MOBILE)).thenReturn(true);

        for (String mobileNumber : new String[] {MOBILE, "+639171234567"}) {
            assertThatThrownBy(() -> userService.createUser(
                    EMAIL, mobileNumber, "Demo User", PIN))
                    .isInstanceOf(MobileNumberAlreadyRegisteredException.class)
                    .hasMessageContaining(MOBILE);
        }

        verify(passwordEncoder, never()).encode(any(String.class));
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void createUserTranslatesConcurrentDuplicateEmailViolation() {
        stubSaveFailure(integrityViolation(UserService.EMAIL_UNIQUE_CONSTRAINT));

        assertThatThrownBy(() -> userService.createUser(EMAIL, MOBILE, "Demo User", PIN))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining(EMAIL);

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void createUserTranslatesConcurrentDuplicateMobileNumberViolation() {
        stubSaveFailure(integrityViolation(UserService.MOBILE_NUMBER_UNIQUE_CONSTRAINT));

        assertThatThrownBy(() -> userService.createUser(EMAIL, MOBILE, "Demo User", PIN))
                .isInstanceOf(MobileNumberAlreadyRegisteredException.class)
                .hasMessageContaining(MOBILE);

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void createUserRethrowsUnrelatedIntegrityViolation() {
        DataIntegrityViolationException unrelated =
                integrityViolation("chk_users_mobile_number_format");
        stubSaveFailure(unrelated);

        assertThatThrownBy(() -> userService.createUser(EMAIL, MOBILE, "Demo User", PIN))
                .isSameAs(unrelated);

        verify(accountRepository, never()).save(any(Account.class));
    }

    private void stubSuccessfulSave() {
        when(passwordEncoder.encode(PIN)).thenReturn("bcrypt-hash");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubSaveFailure(DataIntegrityViolationException exception) {
        when(passwordEncoder.encode(PIN)).thenReturn("bcrypt-hash");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(exception);
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
