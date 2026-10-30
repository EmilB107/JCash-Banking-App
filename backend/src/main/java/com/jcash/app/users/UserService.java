package com.jcash.app.users;

import com.jcash.app.accounts.Account;
import com.jcash.app.accounts.AccountRepository;
import com.jcash.app.users.exception.MobileNumberAlreadyRegisteredException;
import com.jcash.app.users.exception.UserAlreadyExistsException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    static final String EMAIL_UNIQUE_CONSTRAINT = "users_email_key";
    static final String MOBILE_NUMBER_UNIQUE_CONSTRAINT = "uq_users_mobile_number";

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
            String mobileNumber,
            String displayName,
            String rawPin) {

        String normalizedEmail = EmailNormalizer.normalize(email);
        String normalizedMobileNumber = MobileNumberNormalizer.normalize(mobileNumber);

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new UserAlreadyExistsException(normalizedEmail);
        }
        if (userRepository.existsByMobileNumber(normalizedMobileNumber)) {
            throw new MobileNumberAlreadyRegisteredException(normalizedMobileNumber);
        }

        String pinHash = passwordEncoder.encode(rawPin);

        User user = new User(normalizedEmail, normalizedMobileNumber, displayName, pinHash);
        try {
            // Flush so a concurrent registration that passed the exists checks
            // fails here on a unique constraint, not later at commit.
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            String constraintName = violatedConstraint(exception);
            if (EMAIL_UNIQUE_CONSTRAINT.equalsIgnoreCase(constraintName)) {
                throw new UserAlreadyExistsException(normalizedEmail);
            }
            if (MOBILE_NUMBER_UNIQUE_CONSTRAINT.equalsIgnoreCase(constraintName)) {
                throw new MobileNumberAlreadyRegisteredException(normalizedMobileNumber);
            }
            throw exception;
        }

        Account account = new Account(user);
        accountRepository.save(account);

        return user;
    }

    private static String violatedConstraint(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
    }
}
