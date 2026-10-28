package com.certpractice.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;

/**
 * Cookie and CSRF policy for the same-origin SPA: an HttpOnly SESSION cookie
 * plus a readable XSRF-TOKEN cookie sent back in the X-XSRF-TOKEN header.
 */
@Configuration
public class CsrfConfig {

    @Bean
    public CsrfTokenRepository csrfTokenRepository(
            @Value("${app.security.cookie-secure:false}") final boolean cookieSecure) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .sameSite("Lax")
                .secure(cookieSecure));
        return repository;
    }

    @Bean
    public CsrfTokenRequestHandler csrfTokenRequestHandler() {
        return new SpaCsrfTokenRequestHandler();
    }
}
