package com.jcash.app.security;

import com.jcash.app.security.exception.IncorrectPinException;
import com.jcash.app.users.User;
import com.jcash.app.users.UserRepository;
import com.jcash.app.users.exception.UserNotFoundException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Changes a signed-in user's PIN after checking the current one.
 *
 * Wrong current PINs share the login counter and lockout (see LoginService):
 * otherwise someone using an unattended session could guess the PIN here
 * without limit, then use it to log in elsewhere.
 */
@Service
public class PinChangeService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public PinChangeService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    // Row lock: concurrent attempts run one at a time, like LoginService.login.
    // The failed-attempt count must commit even though the request fails.
    @Transactional(noRollbackFor = {IncorrectPinException.class, LockedException.class})
    public void changePin(String email, String currentPin, String newPin) {
        User user = userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        Instant now = clock.instant();
        user.clearExpiredLoginLock(now);
        if (user.isLoginLocked(now)) {
            throw new LockedException("Account is locked");
        }

        if (!passwordEncoder.matches(currentPin, user.getPinHash())) {
            if (user.recordFailedLogin(
                    now, LoginService.MAX_FAILED_ATTEMPTS, LoginService.LOCK_DURATION)) {
                throw new LockedException("Account locked after too many wrong PINs");
            }
            throw new IncorrectPinException(
                    LoginService.MAX_FAILED_ATTEMPTS - user.getFailedLoginAttempts());
        }

        user.changePinHash(passwordEncoder.encode(newPin));
        user.resetFailedLoginAttempts();
    }
}
