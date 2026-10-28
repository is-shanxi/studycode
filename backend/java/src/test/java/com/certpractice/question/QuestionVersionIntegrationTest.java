package com.certpractice.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.certpractice.support.P1TestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class QuestionVersionIntegrationTest extends P1TestSupport {

    @Test
    void publishingNewVersionKeepsHistoricalSessionsAndOfflineKeepsHistory() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        String stem = "versioned stem " + UUID.randomUUID();
        MvcResult created = mockMvc.perform(post("/api/v1/admin/questions")
                        .with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(choicePayload(catalog, stem))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.versions[0].status").value("DRAFT"))
                .andReturn();
        JsonNode createdJson = readJson(created);
        UUID questionId = UUID.fromString(createdJson.path("questionId").asText());
        UUID firstVersionId = UUID.fromString(
                createdJson.path("versions").get(0).path("versionId").asText());

        mockMvc.perform(post("/api/v1/admin/question-versions/{id}/submit-review", firstVersionId)
                        .with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        mockMvc.perform(post("/api/v1/admin/question-versions/{id}/approve", firstVersionId)
                        .with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publishNow\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        UUID userId = registerUser();
        UUID firstSession = createAndCompleteSession(userId, catalog.subjectId(), stem, firstVersionId);

        MvcResult cloneResult = mockMvc.perform(
                        post("/api/v1/admin/questions/{id}/versions", questionId)
                                .with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions.length()").value(2))
                .andReturn();
        UUID secondVersionId = UUID.fromString(
                readJson(cloneResult).path("versions").get(0).path("versionId").asText());

        mockMvc.perform(put("/api/v1/admin/question-versions/{id}", secondVersionId)
                        .with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("stem", stem + " v2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions[0].stem").value(stem + " v2"));

        mockMvc.perform(post("/api/v1/admin/question-versions/{id}/submit-review", secondVersionId)
                        .with(admin()).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/question-versions/{id}/approve", secondVersionId)
                        .with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publishNow\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        UUID secondSession = createSession(userId, catalog.subjectId());
        MvcResult secondView = mockMvc.perform(get("/api/v1/practice-sessions/{id}", secondSession)
                        .with(appUser(userId, "user")))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(readJson(secondView).path("items").get(0).path("question")
                .path("questionVersionId").asText()).isEqualTo(secondVersionId.toString());

        MvcResult firstRecord = mockMvc.perform(get("/api/v1/me/records/{id}", firstSession)
                        .with(appUser(userId, "user")))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(readJson(firstRecord).path("items").get(0).path("questionVersionId").asText())
                .isEqualTo(firstVersionId.toString());
        assertThat(readJson(firstRecord).path("items").get(0).path("stem").asText())
                .isEqualTo(stem);

        mockMvc.perform(post("/api/v1/admin/questions/{id}/offline", questionId)
                        .with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFFLINE"));

        mockMvc.perform(get("/api/v1/questions/{id}", questionId).with(appUser(userId, "user")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/me/records/{id}", firstSession)
                        .with(appUser(userId, "user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].stem").value(stem));

        mockMvc.perform(delete("/api/v1/admin/question-versions/{id}", firstVersionId)
                        .with(admin()).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void draftCanBeRejectedAndDeleted() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        MvcResult created = mockMvc.perform(post("/api/v1/admin/questions")
                        .with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                choicePayload(catalog, "reject me " + UUID.randomUUID()))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode createdJson = readJson(created);
        UUID questionId = UUID.fromString(createdJson.path("questionId").asText());
        UUID versionId = UUID.fromString(
                createdJson.path("versions").get(0).path("versionId").asText());

        mockMvc.perform(post("/api/v1/admin/question-versions/{id}/submit-review", versionId)
                        .with(admin()).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/question-versions/{id}/reject", versionId)
                        .with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"needs work\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(delete("/api/v1/admin/question-versions/{id}", versionId)
                        .with(admin()).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/questions/{id}", questionId).with(admin()))
                .andExpect(status().isNotFound());
    }

    private UUID createAndCompleteSession(
            final UUID userId,
            final UUID subjectId,
            final String stem,
            final UUID expectedVersionId) throws Exception {
        UUID sessionId = createSession(userId, subjectId);
        MvcResult viewResult = mockMvc.perform(get("/api/v1/practice-sessions/{id}", sessionId)
                        .with(appUser(userId, "user")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode item = readJson(viewResult).path("items").get(0);
        assertThat(item.path("question").path("questionVersionId").asText())
                .isEqualTo(expectedVersionId.toString());
        assertThat(item.path("question").path("stem").asText()).isEqualTo(stem);
        UUID optionId = UUID.fromString(item.path("question").path("options").get(0).path("id").asText());
        mockMvc.perform(put("/api/v1/practice-sessions/{id}/items/{itemId}/answer", sessionId,
                        UUID.fromString(item.path("itemId").asText()))
                        .with(appUser(userId, "user"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "answerRevision", 0,
                                "answer", Map.of("selectedOptionId", optionId.toString())))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/practice-sessions/{id}/submit", sessionId)
                        .with(appUser(userId, "user"))
                        .with(csrf()))
                .andExpect(status().isOk());
        return sessionId;
    }

    private UUID createSession(final UUID userId, final UUID subjectId) throws Exception {
        Map<String, Object> body = Map.of(
                "mode", "NORMAL",
                "filters", Map.of("subjectId", subjectId.toString()),
                "requestedCount", 10);
        MvcResult result = mockMvc.perform(post("/api/v1/practice-sessions")
                        .with(appUser(userId, "user"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(result).path("sessionId").asText());
    }

    private Map<String, Object> choicePayload(final SubjectChapter catalog, final String stem) {
        return Map.of(
                "subjectId", catalog.subjectId().toString(),
                "chapterId", catalog.chapterId().toString(),
                "type", "SINGLE_CHOICE",
                "stem", stem,
                "explanation", "Explanation for " + stem,
                "difficulty", "MEDIUM",
                "sourceText", "Test source",
                "options", List.of(
                        Map.of("key", "A", "content", "Option A", "correct", false),
                        Map.of("key", "B", "content", "Option B", "correct", true)),
                "aliases", List.of(),
                "tagIds", List.of());
    }

    private JsonNode readJson(final MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
