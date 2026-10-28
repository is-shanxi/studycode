package com.certpractice.identity;

import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.error.ProblemDetailsWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;

/**
 * Server-side session authentication with PostgreSQL-backed Spring Session,
 * CSRF protection and role gates. Frontend route guards are never authoritative.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(
            final HttpSecurity http,
            final DatabaseAuthenticationProvider authenticationProvider,
            final CsrfTokenRepository csrfTokenRepository,
            final CsrfTokenRequestHandler csrfTokenRequestHandler,
            final ProblemDetailsWriter problemDetailsWriter,
            final SecurityContextRepository securityContextRepository,
            final AppUserRepository users) throws Exception {
        ActiveUserFilter activeUserFilter = new ActiveUserFilter(users, problemDetailsWriter);
        CsrfCookieFilter csrfCookieFilter = new CsrfCookieFilter();

        http
                .securityMatcher("/api/**", "/actuator/**")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/v1/auth/csrf",
                                "/api/v1/auth/register",
                                "/api/v1/auth/sessions",
                                "/api/v1/health",
                                "/actuator/health",
                                "/actuator/health/**")
                        .permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfTokenRequestHandler))
                .securityContext(securityContext -> securityContext
                        .securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .requestCache(AbstractHttpConfigurer::disable)
                .authenticationProvider(authenticationProvider)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                problemDetailsWriter.write(
                                        response,
                                        ErrorCode.UNAUTHENTICATED,
                                        "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) ->
                                problemDetailsWriter.write(
                                        response,
                                        ErrorCode.FORBIDDEN,
                                        "Access is denied")))
                .addFilterAfter(csrfCookieFilter, CsrfFilter.class)
                .addFilterAfter(activeUserFilter, SecurityContextHolderFilter.class)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            final DatabaseAuthenticationProvider authenticationProvider) {
        return new ProviderManager(authenticationProvider);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}
