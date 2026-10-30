package com.jcash.app.users;

import com.jcash.app.accounts.Account;
import com.jcash.app.accounts.AccountRepository;
import com.jcash.app.users.exception.UserAlreadyExistsException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    static final String EMAIL_UNIQUE_CONSTRAINT = "users_email_key";

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User createUser(
            String email,
            String displayName,
            String rawPassword) {

        String normalizedEmail = EmailNormalizer.normalize(email);

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new UserAlreadyExistsException(normalizedEmail);
        }

        String passwordHash = passwordEncoder.encode(rawPassword);

        User user = new User(normalizedEmail, displayName, passwordHash);
        try {
            // Flush so a concurrent registration that passed the existsByEmail
            // check fails here on the unique constraint, not later at commit.
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (violatesConstraint(exception, EMAIL_UNIQUE_CONSTRAINT)) {
                throw new UserAlreadyExistsException(normalizedEmail);
            }
            throw exception;
        }

        Account account = new Account(user);
        accountRepository.save(account);

        return user;
    }

    private static boolean violatesConstraint(
            DataIntegrityViolationException exception,
            String constraintName) {

        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return constraintName.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }
}
