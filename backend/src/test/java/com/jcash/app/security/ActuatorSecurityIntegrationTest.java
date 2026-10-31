package com.jcash.app.security;

import com.jcash.app.common.FreshTestDatabaseConfiguration;
import com.jcash.app.users.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        FreshTestDatabaseConfiguration.DATASOURCE_URL,
        FreshTestDatabaseConfiguration.CLEAN_ENABLED,
        "jcash.actuator.username=ops",
        "jcash.actuator.password=test-secret"
})
@AutoConfigureMockMvc
@Import(FreshTestDatabaseConfiguration.class)
class ActuatorSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void healthIsPublicWithoutDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void operatorSeesHealthDetails() throws Exception {
        mockMvc.perform(get("/actuator/health").with(httpBasic("ops", "test-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    void metricsRequireTheOperatorAccount() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Basic")));
        mockMvc.perform(get("/actuator/metrics").with(httpBasic("ops", "wrong")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics/system.cpu.usage")
                        .with(httpBasic("ops", "test-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("system.cpu.usage"));
    }

    @Test
    void appUserRoleIsNotEnoughForMetrics() throws Exception {
        mockMvc.perform(get("/actuator/metrics").with(user("demo@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void appLoginSessionIsIgnoredByActuator() throws Exception {
        jdbcTemplate.update("DELETE FROM transfer_idempotency_keys");
        jdbcTemplate.update("DELETE FROM transaction_entries");
        jdbcTemplate.update("DELETE FROM accounts");
        jdbcTemplate.update("DELETE FROM users");
        userService.createUser("demo@example.com", "09170000001", "Demo User", "123456");
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"demo@example.com","pin":"123456"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/me").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void operatorAccountCannotUseTheApp() throws Exception {
        mockMvc.perform(get("/api/users/me").with(httpBasic("ops", "test-secret")))
                .andExpect(status().isUnauthorized());
    }
}
