package com.jcash.app.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfiguration.class)
class AuthControllerMvcTest {

    private static final String EMAIL = "demo@example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoginService loginService;

    @Test
    void successfulLoginStoresSecurityContextInSession() throws Exception {
        when(loginService.login(EMAIL, "123456")).thenReturn(authenticated());

        mockMvc.perform(login("{\"identifier\":\"demo@example.com\",\"pin\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(request().sessionAttribute(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                        notNullValue()));
    }

    @Test
    void mobileNumberIdentifierIsPassedToLoginService() throws Exception {
        when(loginService.login("+639171234567", "123456")).thenReturn(authenticated());

        mockMvc.perform(login("{\"identifier\":\"+639171234567\",\"pin\":\"123456\"}"))
                .andExpect(status().isOk());

        verify(loginService).login("+639171234567", "123456");
    }

    @Test
    void wrongPinReturns401() throws Exception {
        when(loginService.login(EMAIL, "000000"))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(login("{\"identifier\":\"demo@example.com\",\"pin\":\"000000\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Email/mobile number or PIN is incorrect"));
    }

    @Test
    void lockedAccountReturns423() throws Exception {
        when(loginService.login(EMAIL, "123456"))
                .thenThrow(new LockedException("User account is locked"));

        mockMvc.perform(login("{\"identifier\":\"demo@example.com\",\"pin\":\"123456\"}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.message").value(containsString("3 failed PIN attempts")))
                .andExpect(jsonPath("$.message").value(containsString("5 minutes")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"pin\":\"123456\"}",
            "{\"identifier\":\"  \",\"pin\":\"123456\"}",
            "{\"identifier\":\"demo@example.com\"}",
            "{\"identifier\":\"demo@example.com\",\"pin\":\"\"}"
    })
    void invalidBodyReturns400WithoutAttemptingLogin(String body) throws Exception {
        mockMvc.perform(login(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        verifyNoInteractions(loginService);
    }

    @Test
    void loginWithoutCsrfTokenIsRejectedWith403() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"demo@example.com\",\"pin\":\"123456\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(loginService);
    }

    @Test
    void logoutReturns204InsteadOfRedirecting() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist("Location"));
    }

    private static MockHttpServletRequestBuilder login(String body) {
        return post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static UsernamePasswordAuthenticationToken authenticated() {
        return UsernamePasswordAuthenticationToken.authenticated(
                EMAIL, null, AuthorityUtils.createAuthorityList("ROLE_USER"));
    }
}
