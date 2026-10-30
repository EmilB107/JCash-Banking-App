package com.jcash.app.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

import java.util.function.Supplier;

// Accepts both forms of the CSRF token in the X-XSRF-TOKEN header: the masked token
// returned by /api/auth/csrf, and the raw XSRF-TOKEN cookie value that Swagger UI and
// Angular send. Spring's spa() handler only accepts the raw value in a header.
final class LenientCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final XorCsrfTokenRequestAttributeHandler masked =
            new XorCsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestAttributeHandler raw =
            new CsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       Supplier<CsrfToken> csrfToken) {
        masked.handle(request, response, csrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String unmasked = masked.resolveCsrfTokenValue(request, csrfToken);
        return unmasked != null ? unmasked : raw.resolveCsrfTokenValue(request, csrfToken);
    }
}
