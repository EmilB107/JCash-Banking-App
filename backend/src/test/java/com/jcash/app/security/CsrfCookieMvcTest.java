package com.jcash.app.security;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Exercises the real XSRF-TOKEN cookie flow, so it must never use the csrf() post-processor:
// that swaps a test repository into the shared filter chain for the rest of the context.
// Swagger UI and Angular send the raw cookie value back as the X-XSRF-TOKEN header.
@WebMvcTest({AuthController.class, CsrfController.class})
@Import(SecurityConfiguration.class)
class CsrfCookieMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoginService loginService;

    @Test
    void loginAcceptsRawCsrfCookieValueAsHeader() throws Exception {
        MvcResult csrfResult = fetchCsrf();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

        login(csrfCookie, csrfCookie.getValue())
                .andExpect(status().isOk());
    }

    @Test
    void loginAcceptsMaskedTokenFromCsrfEndpointAsHeader() throws Exception {
        MvcResult csrfResult = fetchCsrf();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        String maskedToken = JsonPath.read(csrfResult.getResponse().getContentAsString(), "$.token");

        login(csrfCookie, maskedToken)
                .andExpect(status().isOk());
    }

    @Test
    void loginWithoutCsrfHeaderIsRejected() throws Exception {
        Cookie csrfCookie = fetchCsrf().getResponse().getCookie("XSRF-TOKEN");

        mockMvc.perform(post("/api/auth/login")
                        .cookie(csrfCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"demo@example.com\",\"pin\":\"123456\"}"))
                .andExpect(status().isForbidden());
    }

    private MvcResult fetchCsrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getCookie("XSRF-TOKEN")).isNotNull();
        return result;
    }

    private ResultActions login(Cookie csrfCookie, String headerValue) throws Exception {
        when(loginService.login(anyString(), anyString())).thenReturn(
                new UsernamePasswordAuthenticationToken(
                        "demo@example.com", null, AuthorityUtils.NO_AUTHORITIES));

        return mockMvc.perform(post("/api/auth/login")
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", headerValue)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"demo@example.com\",\"pin\":\"123456\"}"));
    }
}
