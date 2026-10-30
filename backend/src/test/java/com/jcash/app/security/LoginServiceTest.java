package com.jcash.app.security;

import com.jcash.app.users.User;
import com.jcash.app.users.UserLookupService;
import com.jcash.app.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

// Uses Spring Security's real DaoAuthenticationProvider and BCrypt so that the
// locked-account pre-check and PIN verification behave exactly as in production.
@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final String EMAIL = "demo@example.com";
    private static final String MOBILE = "09171234567";
    private static final String PIN = "123456";
    private static final String WRONG_PIN = "654321";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(4);

    @Mock
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User(EMAIL, MOBILE, "Demo User", PASSWORD_ENCODER.encode(PIN));

        lenient().when(userRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
        lenient().when(userRepository.findByMobileNumberForUpdate(MOBILE))
                .thenReturn(Optional.of(user));
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    @Test
    void emailLoginSucceedsWithEmailPrincipal() {
        Authentication authentication = loginAt(NOW, "  Demo@Example.COM ", PIN);

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getName()).isEqualTo(EMAIL);
    }

    @Test
    void mobileLoginInEitherFormatSucceedsWithEmailPrincipal() {
        for (String identifier : new String[] {MOBILE, "+639171234567"}) {
            Authentication authentication = loginAt(NOW, identifier, PIN);

            assertThat(authentication.getName()).isEqualTo(EMAIL);
        }
    }

    @Test
    void wrongPinIsRejectedAndCounted() {
        assertThatThrownBy(() -> loginAt(NOW, EMAIL, WRONG_PIN))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void thirdFailureLocksAccountForLockDuration() {
        failTwice();

        assertThatThrownBy(() -> loginAt(NOW, MOBILE, WRONG_PIN))
                .isInstanceOf(LockedException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(LoginService.MAX_FAILED_ATTEMPTS);
        assertThat(user.getLockedUntil()).isEqualTo(NOW.plus(LoginService.LOCK_DURATION));
        assertThat(LoginService.LOCK_DURATION).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void correctPinIsRejectedWhileLocked() {
        lockAccount();

        Instant justBeforeUnlock = NOW.plus(LoginService.LOCK_DURATION).minusSeconds(1);
        assertThatThrownBy(() -> loginAt(justBeforeUnlock, EMAIL, PIN))
                .isInstanceOf(LockedException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(LoginService.MAX_FAILED_ATTEMPTS);
        assertThat(user.getLockedUntil()).isEqualTo(NOW.plus(LoginService.LOCK_DURATION));
    }

    @Test
    void lockExpiresAutomaticallyAndSuccessClearsIt() {
        lockAccount();

        Authentication authentication =
                loginAt(NOW.plus(LoginService.LOCK_DURATION), EMAIL, PIN);

        assertThat(authentication.getName()).isEqualTo(EMAIL);
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void failureAfterExpiredLockStartsAFreshCount() {
        lockAccount();

        assertThatThrownBy(() -> loginAt(NOW.plus(LoginService.LOCK_DURATION), EMAIL, WRONG_PIN))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void successfulLoginResetsEarlierFailures() {
        failTwice();

        loginAt(NOW, EMAIL, PIN);

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void unknownIdentifierIsRejectedAsBadCredentials() {
        for (String identifier : new String[] {"missing@example.com", "09179999999"}) {
            assertThatThrownBy(() -> loginAt(NOW, identifier, PIN))
                    .isInstanceOf(BadCredentialsException.class);
        }

        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    private void failTwice() {
        for (int i = 0; i < LoginService.MAX_FAILED_ATTEMPTS - 1; i++) {
            assertThatThrownBy(() -> loginAt(NOW, EMAIL, WRONG_PIN))
                    .isInstanceOf(BadCredentialsException.class);
        }
    }

    private void lockAccount() {
        failTwice();
        assertThatThrownBy(() -> loginAt(NOW, EMAIL, WRONG_PIN))
                .isInstanceOf(LockedException.class);
    }

    private Authentication loginAt(Instant now, String identifier, String pin) {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(
                new DatabaseUserDetailsService(userRepository, clock));
        provider.setPasswordEncoder(PASSWORD_ENCODER);

        LoginService loginService = new LoginService(
                new ProviderManager(provider),
                new UserLookupService(userRepository),
                clock
        );
        return loginService.login(identifier, pin);
    }
}
