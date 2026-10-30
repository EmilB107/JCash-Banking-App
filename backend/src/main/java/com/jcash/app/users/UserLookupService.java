package com.jcash.app.users;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserLookupService {

    private final UserRepository userRepository;

    public UserLookupService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> findByIdentifier(String identifier) {
        if (identifier == null) {
            return Optional.empty();
        }

        if (isEmail(identifier)) {
            return userRepository.findByEmail(EmailNormalizer.normalize(identifier));
        }
        return userRepository.findByMobileNumber(MobileNumberNormalizer.normalize(identifier));
    }

    // The row lock lasts until the caller's transaction ends, so a caller is required.
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<User> findByIdentifierForUpdate(String identifier) {
        if (identifier == null) {
            return Optional.empty();
        }

        if (isEmail(identifier)) {
            return userRepository.findByEmailForUpdate(EmailNormalizer.normalize(identifier));
        }
        return userRepository.findByMobileNumberForUpdate(
                MobileNumberNormalizer.normalize(identifier));
    }

    private static boolean isEmail(String identifier) {
        return identifier.contains("@");
    }
}
