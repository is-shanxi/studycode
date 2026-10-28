package com.certpractice.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.certpractice.support.P1TestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AnswerVisibilityContractTest extends P1TestSupport {

    @Test
    void practiceSearchAndDetailNeverExposeAnswerFieldsBeforeSubmission() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        QuestionRef choice = createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                "contract choice " + UUID.randomUUID(), "B", List.of());
        createPublishedBlank(catalog.subjectId(), catalog.chapterId(),
                "contract blank " + UUID.randomUUID(), "SYN", List.of(), List.of());
        UUID userId = registerUser();

        Map<String, Object> body = Map.of(
                "mode", "NORMAL",
                "filters", Map.of("subjectId", catalog.subjectId().toString()),
                "requestedCount", 10);
        MvcResult sessionResult = mockMvc.perform(post("/api/v1/practice-sessions")
                        .with(appUser(userId, "user"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        String sessionJson = sessionResult.getResponse().getContentAsString();
        assertSafe(sessionJson);

        MvcResult searchResult = mockMvc.perform(get("/api/v1/questions/search")
                        .with(appUser(userId, "user"))
                        .param("subjectId", catalog.subjectId().toString())
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn();
        assertSafe(searchResult.getResponse().getContentAsString());

        MvcResult detailResult = mockMvc.perform(get("/api/v1/questions/{id}", choice.questionId())
                        .with(appUser(userId, "user")))
                .andExpect(status().isOk())
                .andReturn();
        String detailJson = detailResult.getResponse().getContentAsString();
        assertSafe(detailJson);
        JsonNode detail = objectMapper.readTree(detailJson);
        assertThat(detail.path("options").toString()).doesNotContain("isCorrect");

        MvcResult incomplete = mockMvc.perform(post("/api/v1/practice-sessions/{id}/submit",
                        objectMapper.readTree(sessionJson).path("sessionId").asText())
                        .with(appUser(userId, "user"))
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();
        assertSafe(incomplete.getResponse().getContentAsString());
    }

    private void assertSafe(final String json) {
        assertThat(json).doesNotContain(
                "\"isCorrect\"",
                "\"correctOptionId\"",
                "\"referenceAnswer\"",
                "\"explanation\"",
                "\"aliases\"",
                "\"fillReferenceAnswer\"");
    }
}
