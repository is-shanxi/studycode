package com.certpractice.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public bootstrap endpoint for the SPA. The session cookie remains HttpOnly;
 * only the CSRF token is readable by JavaScript.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class CsrfController {

    private final CsrfTokenRepository csrfTokenRepository;

    public CsrfController(final CsrfTokenRepository csrfTokenRepository) {
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(final HttpServletRequest request, final HttpServletResponse response) {
        CsrfToken csrfToken = csrfTokenRepository.loadToken(request);
        if (csrfToken == null) {
            csrfToken = csrfTokenRepository.generateToken(request);
            csrfTokenRepository.saveToken(csrfToken, request, response);
        }
        return new CsrfTokenResponse(
                csrfToken.getHeaderName(),
                csrfToken.getParameterName(),
                csrfToken.getToken());
    }

    public record CsrfTokenResponse(String headerName, String parameterName, String token) {
    }
}
