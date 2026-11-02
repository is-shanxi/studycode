package com.certpractice.identity;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import com.certpractice.support.P1TestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * Login failures are throttled per normalized username. The lockout must be
 * observable through the public API without revealing whether the username
 * exists, and a successful login must reset the failure counter.
 */
@TestPropertySource(properties = {
        "app.security.login-throttle.max-failures=3",
        "app.security.login-throttle.failure-window=1h",
        "app.security.login-throttle.lock-duration=1h"})
class LoginThrottleIntegrationTest extends P1TestSupport {

    private static final String PASSWORD = "Correct@123";

    @Test
    void repeatedFailuresLockTheAccountAndReturnRateLimited() throws Exception {
        String username = register("lockdown");
        String otherUsername = register("bystander");

        attemptLogin(username, "Wrong@123", 401);
        attemptLogin(username, "Wrong@123", 401);
        attemptLogin(username, "Wrong@123", 401);

        attemptLogin(username, PASSWORD, 429);
        attemptLogin(username, PASSWORD, 429, "LOGIN_LOCKED");

        attemptLogin(otherUsername, "Wrong@123", 401);
        attemptLogin(otherUsername, PASSWORD, 200);
    }

    @Test
    void successfulLoginResetsTheFailureCounter() throws Exception {
        String username = register("reset");

        attemptLogin(username, "Wrong@123", 401);
        attemptLogin(username, "Wrong@123", 401);
        attemptLogin(username, PASSWORD, 200);

        attemptLogin(username, "Wrong@123", 401);
        attemptLogin(username, "Wrong@123", 401);
        attemptLogin(username, PASSWORD, 200);
    }

    @Test
    void unknownUsernamesAreThrottledTheSameWay() throws Exception {
        String unknown = "missing" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        attemptLogin(unknown, "Wrong@123", 401);
        attemptLogin(unknown, "Wrong@123", 401);
        attemptLogin(unknown, "Wrong@123", 401);
        attemptLogin(unknown, "Wrong@123", 429, "LOGIN_LOCKED");
    }

    private String register(final String prefix) throws Exception {
        String username = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Map<String, String> payload = Map.of(
                "username", username,
                "password", PASSWORD,
                "passwordConfirmation", PASSWORD);
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());
        return username;
    }

    private void attemptLogin(
            final String username,
            final String password,
            final int expectedStatus) throws Exception {
        attemptLogin(username, password, expectedStatus, null);
    }

    private void attemptLogin(
            final String username,
            final String password,
            final int expectedStatus,
            final String expectedCode) throws Exception {
        Map<String, String> payload = Map.of("username", username, "password", password);
        var result = mockMvc.perform(post("/api/v1/auth/sessions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().is(expectedStatus));
        if (expectedCode != null) {
            result.andExpect(jsonPath("$.code").value(expectedCode));
        }
    }
}
