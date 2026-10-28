package com.jcash.app.users;

import com.jcash.app.accounts.Account;
import com.jcash.app.accounts.AccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    
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

        if (userRepository.existsByEmail(email)) {
                throw new UserAlreadyExistsException(email);
        }

        String passwordHash = passwordEncoder.encode(rawPassword);

        User user = new User(email, displayName, passwordHash);
        user = userRepository.save(user);

        Account account = new Account(user);
        accountRepository.save(account);

        return user;
    }
}
