package com.jcash.app.security;

import com.jcash.app.users.User;
import com.jcash.app.users.UserLookupService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class LoginService {

    public static final int MAX_FAILED_ATTEMPTS = 3;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(5);

    private final AuthenticationManager authenticationManager;
    private final UserLookupService userLookupService;
    private final Clock clock;

    public LoginService(
            AuthenticationManager authenticationManager,
            UserLookupService userLookupService,
            Clock clock) {
        this.authenticationManager = authenticationManager;
        this.userLookupService = userLookupService;
        this.clock = clock;
    }

    // The user row stays locked for the whole attempt, so concurrent logins for the
    // same user run one at a time and each sees the previous attempt's counter.
    // Failed-attempt updates must commit even though the login itself fails.
    @Transactional(noRollbackFor = AuthenticationException.class)
    public Authentication login(String identifier, String pin) {
        Optional<User> user = userLookupService.findByIdentifierForUpdate(identifier);
        if (user.isEmpty()) {
            // Unknown identifiers still go through Spring Security, which runs a dummy
            // BCrypt check so the response time does not reveal whether a user exists.
            return authenticationManager.authenticate(token(identifier, pin));
        }

        return login(user.get(), pin);
    }

    private Authentication login(User user, String pin) {
        Instant now = clock.instant();
        user.clearExpiredLoginLock(now);

        try {
            Authentication authentication =
                    authenticationManager.authenticate(token(user.getEmail(), pin));
            user.resetFailedLoginAttempts();
            return authentication;
        } catch (BadCredentialsException exception) {
            if (user.recordFailedLogin(now, MAX_FAILED_ATTEMPTS, LOCK_DURATION)) {
                throw new LockedException("Account locked after too many failed login attempts");
            }
            throw exception;
        }
    }

    private static UsernamePasswordAuthenticationToken token(String username, String pin) {
        return UsernamePasswordAuthenticationToken.unauthenticated(username, pin);
    }
}
