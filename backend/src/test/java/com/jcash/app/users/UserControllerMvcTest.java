package com.jcash.app.users;

import com.jcash.app.security.SecurityConfiguration;
import com.jcash.app.users.exception.MobileNumberAlreadyRegisteredException;
import com.jcash.app.users.exception.UserAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfiguration.class)
class UserControllerMvcTest {

    private static final String VALID_BODY = """
            {"email":"demo@example.com","mobileNumber":"+639171234567",
             "displayName":"Demo User","pin":"123456"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void registrationReturns201WithoutCredentialFields() throws Exception {
        when(userService.createUser("demo@example.com", "+639171234567", "Demo User", "123456"))
                .thenReturn(new User("demo@example.com", "09171234567", "Demo User", "hash"));

        mockMvc.perform(register(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.*", hasSize(5)))
                .andExpect(jsonPath("$.email").value("demo@example.com"))
                .andExpect(jsonPath("$.mobileNumber").value("09171234567"))
                .andExpect(jsonPath("$.displayName").value("Demo User"))
                .andExpect(jsonPath("$.pin").doesNotExist())
                .andExpect(jsonPath("$.pinHash").doesNotExist());
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        when(userService.createUser("demo@example.com", "+639171234567", "Demo User", "123456"))
                .thenThrow(new UserAlreadyExistsException("demo@example.com"));

        mockMvc.perform(register(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void duplicateMobileNumberReturns409() throws Exception {
        when(userService.createUser("demo@example.com", "+639171234567", "Demo User", "123456"))
                .thenThrow(new MobileNumberAlreadyRegisteredException("09171234567"));

        mockMvc.perform(register(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("MOBILE_NUMBER_ALREADY_REGISTERED"));
    }

    @Test
    void currentUserReturnsProfileWithoutCredentialFields() throws Exception {
        when(userService.findAuthenticatedUser("demo@example.com"))
                .thenReturn(new User("demo@example.com", "09171234567", "Demo User", "hash"));

        mockMvc.perform(get("/api/users/me").with(user("demo@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(5)))
                .andExpect(jsonPath("$.email").value("demo@example.com"))
                .andExpect(jsonPath("$.mobileNumber").value("09171234567"))
                .andExpect(jsonPath("$.displayName").value("Demo User"))
                .andExpect(jsonPath("$.pinHash").doesNotExist())
                .andExpect(jsonPath("$.failedLoginAttempts").doesNotExist())
                .andExpect(jsonPath("$.lockedUntil").doesNotExist());
    }

    @Test
    void currentUserWithoutSessionReturns401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));

        verifyNoInteractions(userService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"email\":\"demo@example.com\",\"displayName\":\"Demo\",\"pin\":\"123456\"}",
            "{\"email\":\"demo@example.com\",\"mobileNumber\":\"9171234567\","
                    + "\"displayName\":\"Demo\",\"pin\":\"123456\"}",
            "{\"email\":\"demo@example.com\",\"mobileNumber\":\"09171234567\","
                    + "\"displayName\":\"Demo\",\"pin\":\"12345\"}",
            "{\"email\":\"demo@example.com\",\"mobileNumber\":\"09171234567\","
                    + "\"displayName\":\"Demo\",\"pin\":\"abcdef\"}",
            "{\"email\":\"demo@example.com\",\"mobileNumber\":\"09171234567\","
                    + "\"displayName\":\"Demo\",\"password\":\"password123\"}"
    })
    void invalidBodyReturns400WithoutCreatingUser(String body) throws Exception {
        mockMvc.perform(register(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        verifyNoInteractions(userService);
    }

    private static MockHttpServletRequestBuilder register(String body) {
        return post("/api/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
