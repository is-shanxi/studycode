package com.certpractice.identity;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import com.certpractice.support.P1TestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AuthIntegrationTest extends P1TestSupport {

    @Test
    void registrationIgnoresClientSuppliedAdminRole() throws Exception {
        String username = "escalate" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, String> payload = Map.of(
                "username", username,
                "password", "Password@123",
                "passwordConfirmation", "Password@123",
                "role", "ADMIN");

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception {
        String username = "dupe" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, String> payload = Map.of(
                "username", username,
                "password", "Password@123",
                "passwordConfirmation", "Password@123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
    }

    @Test
    void loginWithSeedUserCreatesSession() throws Exception {
        Map<String, String> payload = Map.of("username", "student01", "password", "Student@12345");

        mockMvc.perform(post("/api/v1/auth/sessions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("student01"))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    @Test
    void wrongPasswordReturnsUnauthenticated() throws Exception {
        Map<String, String> payload = Map.of("username", "student01", "password", "wrong-password");

        mockMvc.perform(post("/api/v1/auth/sessions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void ordinaryUserCannotCallAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/questions").with(appUser(STUDENT_ID, "student01")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestCannotReadCurrentUser() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administratorCannotDeleteSelf() throws Exception {
        mockMvc.perform(delete("/api/v1/me").with(admin()).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_CANNOT_DELETE_SELF"));
    }

    @Test
    void deletedUserSessionIsRejectedOnNextRequest() throws Exception {
        UUID userId = registerUser();

        mockMvc.perform(delete("/api/v1/me")
                        .with(appUser(userId, "deleted-user"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").with(appUser(userId, "deleted-user")))
                .andExpect(status().isUnauthorized());
    }
}
