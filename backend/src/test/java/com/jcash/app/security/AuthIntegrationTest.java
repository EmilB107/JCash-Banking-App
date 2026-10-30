package com.jcash.app.security;

import com.jcash.app.common.FreshTestDatabaseConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Same properties and import as the other PostgreSQL tests so the cached context
// (and the single test-database reset) is shared; MockMvc is built from it by hand.
@SpringBootTest(properties = {
        FreshTestDatabaseConfiguration.DATASOURCE_URL,
        FreshTestDatabaseConfiguration.CLEAN_ENABLED
})
@Import(FreshTestDatabaseConfiguration.class)
class AuthIntegrationTest {

    private static final String EMAIL = "demo@example.com";
    private static final String MOBILE = "09171234567";
    private static final String PIN = "123456";
    private static final String WRONG_PIN = "654321";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private LoginService loginService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM transfer_idempotency_keys");
        jdbcTemplate.update("DELETE FROM transaction_entries");
        jdbcTemplate.update("DELETE FROM accounts");
        jdbcTemplate.update("DELETE FROM users");

        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        register(EMAIL, "+639171234567").andExpect(status().isCreated())
                .andExpect(jsonPath("$.mobileNumber").value(MOBILE));
    }

    @Test
    void loginByEmailEstablishesSessionForEmailPrincipal() throws Exception {
        MockHttpSession session = loginSession(" Demo@Example.COM ", PIN);

        mockMvc.perform(get("/api/users/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.mobileNumber").value(MOBILE))
                .andExpect(jsonPath("$.displayName").value("Demo User"));
    }

    @Test
    void loginByMobileNumberInEitherFormatEstablishesSessionForEmailPrincipal() throws Exception {
        for (String identifier : new String[] {MOBILE, "+639171234567"}) {
            MockHttpSession session = loginSession(identifier, PIN);

            mockMvc.perform(get("/api/users/me").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(EMAIL));
        }
    }

    @Test
    void wrongPinReturns401AndIsCounted() throws Exception {
        login(EMAIL, WRONG_PIN)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));

        assertThat(failedAttempts()).isEqualTo(1);
        assertThat(lockedUntil()).isNull();
    }

    @Test
    void unknownIdentifierReturns401WithoutTouchingExistingUser() throws Exception {
        login("missing@example.com", PIN).andExpect(status().isUnauthorized());
        login("09179999999", PIN).andExpect(status().isUnauthorized());

        assertThat(failedAttempts()).isZero();
    }

    @Test
    void thirdFailureLocksAccountAndCorrectPinIsRejectedWhileLocked() throws Exception {
        Instant before = Instant.now();
        login(EMAIL, WRONG_PIN).andExpect(status().isUnauthorized());
        login(MOBILE, WRONG_PIN).andExpect(status().isUnauthorized());
        login("+639171234567", WRONG_PIN)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error").value("ACCOUNT_LOCKED"));

        login(EMAIL, PIN)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error").value("ACCOUNT_LOCKED"));

        assertThat(failedAttempts()).isEqualTo(LoginService.MAX_FAILED_ATTEMPTS);
        // One second of slack covers PostgreSQL's microsecond rounding of timestamptz.
        assertThat(lockedUntil()).isBetween(
                before.plus(LoginService.LOCK_DURATION).minusSeconds(1),
                Instant.now().plus(LoginService.LOCK_DURATION).plusSeconds(1));
    }

    @Test
    void expiredLockUnlocksAutomaticallyAndSuccessResetsCounter() throws Exception {
        for (int i = 0; i < LoginService.MAX_FAILED_ATTEMPTS; i++) {
            login(EMAIL, WRONG_PIN);
        }
        assertThat(lockedUntil()).isNotNull();

        // Simulate the lock duration passing.
        jdbcTemplate.update(
                "UPDATE users SET locked_until = now() - interval '1 second' WHERE email = ?",
                EMAIL);

        login(EMAIL, PIN).andExpect(status().isOk());

        assertThat(failedAttempts()).isZero();
        assertThat(lockedUntil()).isNull();
    }

    @Test
    void successfulLoginResetsEarlierFailures() throws Exception {
        login(EMAIL, WRONG_PIN).andExpect(status().isUnauthorized());
        login(EMAIL, WRONG_PIN).andExpect(status().isUnauthorized());

        login(MOBILE, PIN).andExpect(status().isOk());
        assertThat(failedAttempts()).isZero();

        // A fresh count: two more failures still do not lock.
        login(EMAIL, WRONG_PIN).andExpect(status().isUnauthorized());
        login(EMAIL, WRONG_PIN).andExpect(status().isUnauthorized());
        assertThat(lockedUntil()).isNull();
    }

    @Test
    void duplicateMobileNumberInOtherFormatReturns409() throws Exception {
        register("other@example.com", MOBILE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("MOBILE_NUMBER_ALREADY_REGISTERED"));
    }

    @Test
    void concurrentWrongPinsCountExactlyUpToTheLimit() throws Exception {
        int attempts = 10;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return loginService.login(EMAIL, WRONG_PIN);
                }));
            }
            start.countDown();

            int badCredentials = 0;
            int locked = 0;
            for (Future<?> result : results) {
                try {
                    result.get(30, TimeUnit.SECONDS);
                } catch (ExecutionException exception) {
                    if (exception.getCause() instanceof BadCredentialsException) {
                        badCredentials++;
                    } else if (exception.getCause() instanceof LockedException) {
                        locked++;
                    } else {
                        throw exception;
                    }
                }
            }

            assertThat(badCredentials).isEqualTo(LoginService.MAX_FAILED_ATTEMPTS - 1);
            assertThat(locked).isEqualTo(attempts - (LoginService.MAX_FAILED_ATTEMPTS - 1));
            assertThat(failedAttempts()).isEqualTo(LoginService.MAX_FAILED_ATTEMPTS);
        } finally {
            executor.shutdownNow();
        }
    }

    private ResultActions register(String email, String mobileNumber) throws Exception {
        return mockMvc.perform(post("/api/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"mobileNumber\":\"" + mobileNumber
                        + "\",\"displayName\":\"Demo User\",\"pin\":\"" + PIN + "\"}"));
    }

    private ResultActions login(String identifier, String pin) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + identifier + "\",\"pin\":\"" + pin + "\"}"));
    }

    private MockHttpSession loginSession(String identifier, String pin) throws Exception {
        return (MockHttpSession) login(identifier, pin)
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    }

    private int failedAttempts() {
        return jdbcTemplate.queryForObject(
                "SELECT failed_login_attempts FROM users WHERE email = ?", Integer.class, EMAIL);
    }

    private Instant lockedUntil() {
        Timestamp lockedUntil = jdbcTemplate.queryForObject(
                "SELECT locked_until FROM users WHERE email = ?", Timestamp.class, EMAIL);
        return lockedUntil == null ? null : lockedUntil.toInstant();
    }
}
