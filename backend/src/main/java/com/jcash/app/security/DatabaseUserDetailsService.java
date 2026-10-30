package com.jcash.app.security;

import com.jcash.app.users.EmailNormalizer;
import com.jcash.app.users.User;
import com.jcash.app.users.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final Clock clock;

    public DatabaseUserDetailsService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        String normalizedEmail = EmailNormalizer.normalize(email);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found with email: " + normalizedEmail));

        // A locked account is rejected by Spring Security before the PIN is checked.
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPinHash())
                .accountLocked(user.isLoginLocked(clock.instant()))
                .roles("USER")
                .build();
    }
}
