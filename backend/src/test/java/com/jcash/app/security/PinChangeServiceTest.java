package com.jcash.app.security;

import com.jcash.app.security.exception.IncorrectPinException;
import com.jcash.app.users.User;
import com.jcash.app.users.UserRepository;
import com.jcash.app.users.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

// Real BCrypt (cost 4 to keep it fast) so PIN matching behaves as in production.
@ExtendWith(MockitoExtension.class)
class PinChangeServiceTest {

    private static final String EMAIL = "demo@example.com";
    private static final String PIN = "123456";
    private static final String NEW_PIN = "246810";
    private static final String WRONG_PIN = "654321";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(4);

    @Mock
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User(EMAIL, "09171234567", "Demo User", PASSWORD_ENCODER.encode(PIN));
        lenient().when(userRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
    }

    @Test
    void correctCurrentPinStoresHashOfNewPin() {
        changePinAt(NOW, PIN, NEW_PIN);

        assertThat(user.getPinHash()).isNotEqualTo(NEW_PIN);
        assertThat(PASSWORD_ENCODER.matches(NEW_PIN, user.getPinHash())).isTrue();
        assertThat(PASSWORD_ENCODER.matches(PIN, user.getPinHash())).isFalse();
    }

    @Test
    void wrongCurrentPinIsCountedAndKeepsOldPin() {
        String oldHash = user.getPinHash();

        assertThatThrownBy(() -> changePinAt(NOW, WRONG_PIN, NEW_PIN))
                .isInstanceOf(IncorrectPinException.class)
                .hasMessageContaining("2 attempts left");

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getPinHash()).isEqualTo(oldHash);
    }

    @Test
    void thirdWrongPinLocksTheAccount() {
        assertThatThrownBy(() -> changePinAt(NOW, WRONG_PIN, NEW_PIN))
                .isInstanceOf(IncorrectPinException.class);
        assertThatThrownBy(() -> changePinAt(NOW, WRONG_PIN, NEW_PIN))
                .isInstanceOf(IncorrectPinException.class)
                .hasMessageContaining("1 attempt left");
        assertThatThrownBy(() -> changePinAt(NOW, WRONG_PIN, NEW_PIN))
                .isInstanceOf(LockedException.class);

        assertThat(user.isLoginLocked(NOW)).isTrue();
        assertThat(user.getLockedUntil()).isEqualTo(NOW.plus(LoginService.LOCK_DURATION));
    }

    @Test
    void lockedAccountRejectsEvenTheCorrectPin() {
        user.recordFailedLogin(NOW, 1, LoginService.LOCK_DURATION);

        assertThatThrownBy(() -> changePinAt(NOW.plusSeconds(60), PIN, NEW_PIN))
                .isInstanceOf(LockedException.class);

        assertThat(PASSWORD_ENCODER.matches(PIN, user.getPinHash())).isTrue();
    }

    @Test
    void expiredLockAllowsChangeAndSuccessResetsCounter() {
        user.recordFailedLogin(NOW, 1, LoginService.LOCK_DURATION);

        changePinAt(NOW.plus(LoginService.LOCK_DURATION), PIN, NEW_PIN);

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(PASSWORD_ENCODER.matches(NEW_PIN, user.getPinHash())).isTrue();
    }

    @Test
    void unknownUserIsRejected() {
        when(userRepository.findByEmailForUpdate("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> new PinChangeService(
                userRepository, PASSWORD_ENCODER, Clock.fixed(NOW, ZoneOffset.UTC))
                .changePin("missing@example.com", PIN, NEW_PIN))
                .isInstanceOf(UserNotFoundException.class);
    }

    private void changePinAt(Instant now, String currentPin, String newPin) {
        new PinChangeService(userRepository, PASSWORD_ENCODER, Clock.fixed(now, ZoneOffset.UTC))
                .changePin(EMAIL, currentPin, newPin);
    }
}
