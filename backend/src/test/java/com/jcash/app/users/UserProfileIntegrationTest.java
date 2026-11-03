package com.jcash.app.users;

import com.jcash.app.common.FreshTestDatabaseConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Same properties and import as the other PostgreSQL tests so the cached context is shared.
@SpringBootTest(properties = {
        FreshTestDatabaseConfiguration.DATASOURCE_URL,
        FreshTestDatabaseConfiguration.CLEAN_ENABLED
})
@Import(FreshTestDatabaseConfiguration.class)
class UserProfileIntegrationTest {

    private static final String EMAIL = "demo@example.com";
    private static final String MOBILE = "09171234567";
    private static final String PIN = "123456";
    private static final String NEW_PIN = "246810";
    private static final String WRONG_PIN = "654321";

    @Autowired
    private WebApplicationContext context;

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
        register(EMAIL, MOBILE).andExpect(status().isCreated());
    }

    @Test
    void emailChangeKeepsTheSessionSignedIn() throws Exception {
        MockHttpSession session = loginSession(EMAIL, PIN);

        updateProfile(session, "New@Example.COM", "+639181234567", "New Name")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.mobileNumber").value("09181234567"));

        // Same session, now known by the new email.
        mockMvc.perform(get("/api/users/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.displayName").value("New Name"));
        mockMvc.perform(get("/api/accounts/me").session(session))
                .andExpect(status().isOk());

        login("new@example.com", PIN).andExpect(status().isOk());
        login("09181234567", PIN).andExpect(status().isOk());
        login(EMAIL, PIN).andExpect(status().isUnauthorized());
    }

    @Test
    void otherSessionsAreSignedOutAfterAnEmailChange() throws Exception {
        MockHttpSession phone = loginSession(EMAIL, PIN);
        MockHttpSession laptop = loginSession(MOBILE, PIN);

        updateProfile(phone, "new@example.com", MOBILE, "Demo User")
                .andExpect(status().isOk());

        // The laptop's session still holds the old email, which matches nobody now.
        mockMvc.perform(get("/api/users/me").session(laptop))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
        assertThat(laptop.isInvalid()).isTrue();

        mockMvc.perform(get("/api/accounts/me").session(phone))
                .andExpect(status().isOk());
    }

    @Test
    void emailOrMobileOfAnotherUserReturns409() throws Exception {
        register("other@example.com", "09189999999").andExpect(status().isCreated());
        MockHttpSession session = loginSession(EMAIL, PIN);

        updateProfile(session, "OTHER@example.com", MOBILE, "Demo User")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_REGISTERED"));
        updateProfile(session, EMAIL, "+639189999999", "Demo User")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("MOBILE_NUMBER_ALREADY_REGISTERED"));

        mockMvc.perform(get("/api/users/me").session(session))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.mobileNumber").value(MOBILE));
    }

    @Test
    void changedPinIsRequiredForTheNextLogin() throws Exception {
        MockHttpSession session = loginSession(EMAIL, PIN);

        changePin(session, PIN, NEW_PIN).andExpect(status().isNoContent());

        login(EMAIL, PIN).andExpect(status().isUnauthorized());
        login(EMAIL, NEW_PIN).andExpect(status().isOk());
    }

    @Test
    void wrongCurrentPinsShareTheLoginLockout() throws Exception {
        MockHttpSession session = loginSession(EMAIL, PIN);

        // The counter must survive the failed request (no rollback).
        changePin(session, WRONG_PIN, NEW_PIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INCORRECT_PIN"));
        assertThat(failedAttempts()).isEqualTo(1);

        login(EMAIL, WRONG_PIN).andExpect(status().isUnauthorized());
        changePin(session, WRONG_PIN, NEW_PIN)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error").value("ACCOUNT_LOCKED"));

        // Locked for login too, even with the correct PIN.
        login(EMAIL, PIN).andExpect(status().isLocked());
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

    private ResultActions updateProfile(
            MockHttpSession session, String email, String mobileNumber, String displayName)
            throws Exception {
        return mockMvc.perform(put("/api/users/me")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"mobileNumber\":\"" + mobileNumber
                        + "\",\"displayName\":\"" + displayName + "\"}"));
    }

    private ResultActions changePin(MockHttpSession session, String currentPin, String newPin)
            throws Exception {
        return mockMvc.perform(put("/api/users/me/pin")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPin\":\"" + currentPin + "\",\"newPin\":\"" + newPin + "\"}"));
    }

    private int failedAttempts() {
        return jdbcTemplate.queryForObject(
                "SELECT failed_login_attempts FROM users WHERE mobile_number = ?",
                Integer.class, MOBILE);
    }
}
