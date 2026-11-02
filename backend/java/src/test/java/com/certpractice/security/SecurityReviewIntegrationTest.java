package com.certpractice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.certpractice.notification.Notification;
import com.certpractice.notification.NotificationRepository;
import com.certpractice.support.P1TestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Cross-cutting security review: authorization, CSRF, session invalidation and
 * answer-safe responses are verified together so a single regression in the
 * filter chain or DTO mapper is caught before release.
 */
class SecurityReviewIntegrationTest extends P1TestSupport {

    @Autowired
    private NotificationRepository notifications;

    @Test
    void csrfProtectionRejectsUnsafeRequestsWithoutToken() throws Exception {
        UUID userId = registerUser();

        mockMvc.perform(post("/api/v1/practice-sessions")
                        .with(appUser(userId, "csrf-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "mode", "NORMAL",
                                "requestedCount", 1))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/v1/me").with(appUser(userId, "csrf-user")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedProblemResponsesExposeNoPrivateData() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void everyAdminSurfaceRejectsOrdinaryUsers() throws Exception {
        UUID userId = registerUser();

        for (String path : List.of(
                "/api/v1/admin/questions",
                "/api/v1/admin/users",
                "/api/v1/admin/dashboard",
                "/api/v1/admin/audit-logs",
                "/api/v1/admin/reports",
                "/api/v1/admin/configs",
                "/api/v1/admin/ai/generation-batches")) {
            mockMvc.perform(get(path).with(appUser(userId, "ordinary")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    @Test
    void ownershipIsEnforcedForSessionsRecordsNotificationsAndAiConversations() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                "ownership choice " + UUID.randomUUID(), "B", List.of());
        UUID ownerId = registerUser();
        UUID otherId = registerUser();
        UUID sessionId = createSession(ownerId, catalog.subjectId());

        mockMvc.perform(get("/api/v1/practice-sessions/{id}", sessionId)
                        .with(appUser(otherId, "other-user")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/v1/me/records/{id}", sessionId)
                        .with(appUser(otherId, "other-user")))
                .andExpect(status().isNotFound());

        Notification notification = new Notification();
        notification.setRecipientUserId(ownerId);
        notification.setType(Notification.Type.SYSTEM);
        notification.setTitle("ownership");
        notification.setBody("ownership notification");
        notifications.save(notification);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/me/notifications/{id}/read", notification.getId())
                        .with(appUser(otherId, "other-user"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        MvcResult conversation = mockMvc.perform(post("/api/v1/ai/conversations")
                        .with(appUser(ownerId, "ai-owner"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("scope", "GENERAL"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID conversationId = UUID.fromString(objectMapper.readTree(
                conversation.getResponse().getContentAsString()).path("conversationId").asText());

        mockMvc.perform(get("/api/v1/ai/conversations/{id}", conversationId)
                        .with(appUser(otherId, "other-user")))
                .andExpect(status().isNotFound());
    }

    @Test
    void disabledUsersLoseTheirExistingSession() throws Exception {
        UUID userId = registerUser();

        mockMvc.perform(post("/api/v1/admin/users/{id}/disable", userId)
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        mockMvc.perform(get("/api/v1/auth/me").with(appUser(userId, "disabled-user")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void logoutInvalidatesTheServerSideSession() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/sessions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "student01", "password", "Student@12345"))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie[] cookies = login.getResponse().getCookies();
        assertThat(cookies).isNotEmpty();

        mockMvc.perform(get("/api/v1/auth/me").cookie(cookies))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/auth/sessions/current").cookie(cookies).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").cookie(cookies))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void searchAndInProgressSessionResponsesRemainAnswerSafe() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                "leak review choice " + UUID.randomUUID(), "B", List.of());
        createPublishedBlank(catalog.subjectId(), catalog.chapterId(),
                "leak review blank " + UUID.randomUUID(), "SYN", List.of("同步"), List.of());
        UUID userId = registerUser();
        UUID sessionId = createSession(userId, catalog.subjectId());

        String searchBody = mockMvc.perform(get("/api/v1/questions/search")
                        .with(appUser(userId, "search-user"))
                        .param("subjectId", catalog.subjectId().toString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String sessionBody = mockMvc.perform(get("/api/v1/practice-sessions/{id}", sessionId)
                        .with(appUser(userId, "search-user")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        for (String body : List.of(searchBody, sessionBody)) {
            assertThat(body).doesNotContain(
                    "isCorrect", "correctOptionId", "referenceAnswer",
                    "fillReferenceAnswer", "explanation", "aliases");
        }
    }

    private UUID createSession(final UUID userId, final UUID subjectId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/practice-sessions")
                        .with(appUser(userId, "owner-user"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "mode", "NORMAL",
                                "filters", Map.of("subjectId", subjectId.toString()),
                                "requestedCount", 10))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(
                result.getResponse().getContentAsString()).path("sessionId").asText());
    }
}
