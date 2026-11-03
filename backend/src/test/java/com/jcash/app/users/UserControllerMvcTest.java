package com.jcash.app.users;

import com.jcash.app.security.PinChangeService;
import com.jcash.app.security.SecurityConfiguration;
import com.jcash.app.security.exception.IncorrectPinException;
import com.jcash.app.users.exception.MobileNumberAlreadyRegisteredException;
import com.jcash.app.users.exception.UserAlreadyExistsException;
import com.jcash.app.users.exception.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    private static final String PROFILE_BODY = """
            {"email":"new@example.com","mobileNumber":"09181234567","displayName":"New Name"}
            """;

    private static final String PIN_BODY = """
            {"currentPin":"123456","newPin":"654321"}
            """;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private PinChangeService pinChangeService;

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
    void staleSessionReturns401AndIsEnded() throws Exception {
        when(userService.findAuthenticatedUser("old@example.com"))
                .thenThrow(new UserNotFoundException("old@example.com"));
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(get("/api/users/me").session(session).with(user("old@example.com")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));

        assertThat(session.isInvalid()).isTrue();
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

    @Test
    void updateProfileReturnsUpdatedUser() throws Exception {
        when(userService.updateProfile(
                "demo@example.com", "new@example.com", "09181234567", "New Name"))
                .thenReturn(new User("new@example.com", "09181234567", "New Name", "hash"));

        mockMvc.perform(updateProfile(PROFILE_BODY).with(user("demo@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.mobileNumber").value("09181234567"))
                .andExpect(jsonPath("$.displayName").value("New Name"))
                .andExpect(jsonPath("$.pinHash").doesNotExist());
    }

    @Test
    void emailChangeRenamesTheSessionPrincipal() throws Exception {
        when(userService.updateProfile(
                "demo@example.com", "new@example.com", "09181234567", "New Name"))
                .thenReturn(new User("new@example.com", "09181234567", "New Name", "hash"));
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(updateProfile(PROFILE_BODY)
                        .session(session)
                        .with(user("demo@example.com")))
                .andExpect(status().isOk());

        SecurityContext saved = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(saved.getAuthentication().getName()).isEqualTo("new@example.com");
        assertThat(saved.getAuthentication().isAuthenticated()).isTrue();
    }

    @Test
    void updateProfileWithTakenEmailReturns409() throws Exception {
        when(userService.updateProfile(
                "demo@example.com", "new@example.com", "09181234567", "New Name"))
                .thenThrow(new UserAlreadyExistsException("new@example.com"));

        mockMvc.perform(updateProfile(PROFILE_BODY).with(user("demo@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_REGISTERED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"mobileNumber\":\"09181234567\",\"displayName\":\"New Name\"}",
            "{\"email\":\"a@b\",\"mobileNumber\":\"09181234567\",\"displayName\":\"N\"}",
            "{\"email\":\"new@example.com\",\"mobileNumber\":\"9181234567\","
                    + "\"displayName\":\"New Name\"}",
            "{\"email\":\"new@example.com\",\"mobileNumber\":\"09181234567\","
                    + "\"displayName\":\" \"}"
    })
    void invalidProfileBodyReturns400WithoutUpdating(String body) throws Exception {
        mockMvc.perform(updateProfile(body).with(user("demo@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        verifyNoInteractions(userService);
    }

    @Test
    void updateProfileWithoutSessionReturns401() throws Exception {
        mockMvc.perform(updateProfile(PROFILE_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    void changePinReturns204() throws Exception {
        mockMvc.perform(changePin(PIN_BODY).with(user("demo@example.com")))
                .andExpect(status().isNoContent());

        verify(pinChangeService).changePin("demo@example.com", "123456", "654321");
    }

    @Test
    void wrongCurrentPinReturns400NotUnauthorized() throws Exception {
        doThrow(new IncorrectPinException(2))
                .when(pinChangeService).changePin("demo@example.com", "123456", "654321");

        mockMvc.perform(changePin(PIN_BODY).with(user("demo@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INCORRECT_PIN"))
                .andExpect(jsonPath("$.message").value(
                        "Current PIN is incorrect. 2 attempts left before your account is locked."));
    }

    @Test
    void lockedAccountReturns423OnPinChange() throws Exception {
        doThrow(new LockedException("locked"))
                .when(pinChangeService).changePin("demo@example.com", "123456", "654321");

        mockMvc.perform(changePin(PIN_BODY).with(user("demo@example.com")))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error").value("ACCOUNT_LOCKED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"newPin\":\"654321\"}",
            "{\"currentPin\":\"123456\"}",
            "{\"currentPin\":\"123456\",\"newPin\":\"12345\"}",
            "{\"currentPin\":\"123456\",\"newPin\":\"abcdef\"}"
    })
    void invalidPinBodyReturns400WithoutChecking(String body) throws Exception {
        mockMvc.perform(changePin(body).with(user("demo@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        verifyNoInteractions(pinChangeService);
    }

    private static MockHttpServletRequestBuilder updateProfile(String body) {
        return put("/api/users/me")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static MockHttpServletRequestBuilder changePin(String body) {
        return put("/api/users/me/pin")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static MockHttpServletRequestBuilder register(String body) {
        return post("/api/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
