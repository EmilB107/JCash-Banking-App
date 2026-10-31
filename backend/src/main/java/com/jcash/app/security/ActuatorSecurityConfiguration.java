package com.jcash.app.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;

// Actuator has its own operator account, separate from app users: it lives in
// memory (not the users table), uses HTTP Basic, and never creates a session.
// The account is deliberately not a UserDetailsService bean, so it cannot
// replace the database-backed login used by the app.
@Configuration
public class ActuatorSecurityConfiguration {

    public static final String ACTUATOR_ROLE = "ACTUATOR";

    @Bean
    @Order(1)
    SecurityFilterChain actuatorSecurityFilterChain(
            HttpSecurity http,
            PasswordEncoder passwordEncoder,
            @Value("${jcash.actuator.username}") String username,
            @Value("${jcash.actuator.password}") String password)
            throws Exception {
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                    "ACTUATOR_USERNAME and ACTUATOR_PASSWORD must not be blank");
        }

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(
                new InMemoryUserDetailsManager(User.withUsername(username)
                        .password(passwordEncoder.encode(password))
                        .roles(ACTUATOR_ROLE)
                        .build())
        );
        provider.setPasswordEncoder(passwordEncoder);

        http
            .securityMatcher("/actuator/**")
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .anyRequest().hasRole(ACTUATOR_ROLE)
            )
            .authenticationManager(new ProviderManager(provider))
            .httpBasic(Customizer.withDefaults())
            // Only read-only endpoints are exposed and nothing uses cookies here.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            // Ignore any app login session, so only the operator account counts.
            .securityContext(context -> context
                .securityContextRepository(
                    new RequestAttributeSecurityContextRepository()
                )
            );

        return http.build();
    }
}
