package com.certpractice.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.certpractice.support.P1TestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Guards the batched DTO mapper against a return of per-item N+1 lookups. The
 * thresholds are regression guards, not product performance acceptance limits.
 */
class PracticeDtoQueryCountTest extends P1TestSupport {

    private static final int ITEM_COUNT = 10;
    private static final long MAX_SESSION_STATEMENTS = 25L;
    private static final long MAX_RESULT_STATEMENTS = 25L;
    private static final long MAX_RECORD_LIST_STATEMENTS = 30L;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void sessionResultAndRecordDtosUseBatchedQueries() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        UUID firstTag = createTag("batch-tag-a-" + UUID.randomUUID());
        UUID secondTag = createTag("batch-tag-b-" + UUID.randomUUID());
        for (int index = 0; index < ITEM_COUNT; index++) {
            createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                    "batch question " + index + " " + UUID.randomUUID(), "B",
                    List.of(firstTag, secondTag));
        }
        UUID userId = registerUser();
        UUID sessionId = createSession(userId, catalog.subjectId());

        JsonNode session = readJson(mockMvc.perform(
                        get("/api/v1/practice-sessions/{id}", sessionId)
                                .with(appUser(userId, "batch-user")))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(session.path("items").size()).isEqualTo(ITEM_COUNT);

        for (JsonNode item : session.path("items")) {
            UUID itemId = UUID.fromString(item.path("itemId").asText());
            UUID optionId = UUID.fromString(
                    item.path("question").path("options").get(0).path("id").asText());
            mockMvc.perform(put("/api/v1/practice-sessions/{id}/items/{itemId}/answer",
                                    sessionId, itemId)
                            .with(appUser(userId, "batch-user"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "answerRevision", 0,
                                    "answer", Map.of(
                                            "selectedOptionId", optionId.toString())))))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/practice-sessions/{id}/submit", sessionId)
                        .with(appUser(userId, "batch-user"))
                        .with(csrf()))
                .andExpect(status().isOk());

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);

        assertStatementCount(statistics, () -> mockMvc.perform(
                        get("/api/v1/practice-sessions/{id}", sessionId)
                                .with(appUser(userId, "batch-user")))
                .andExpect(status().isOk()), MAX_SESSION_STATEMENTS, "session view");
        assertStatementCount(statistics, () -> mockMvc.perform(
                        get("/api/v1/practice-sessions/{id}/result", sessionId)
                                .with(appUser(userId, "batch-user")))
                .andExpect(status().isOk()), MAX_RESULT_STATEMENTS, "result view");
        assertStatementCount(statistics, () -> mockMvc.perform(
                        get("/api/v1/me/records")
                                .with(appUser(userId, "batch-user")))
                .andExpect(status().isOk()), MAX_RECORD_LIST_STATEMENTS, "record list");

        statistics.setStatisticsEnabled(false);
    }

    private void assertStatementCount(
            final Statistics statistics,
            final RequestAction action,
            final long limit,
            final String label) throws Exception {
        statistics.clear();
        action.run();
        long statements = statistics.getPrepareStatementCount();
        assertThat(statements)
                .as("%s statement count", label)
                .isLessThanOrEqualTo(limit);
    }

    private UUID createSession(final UUID userId, final UUID subjectId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/practice-sessions")
                        .with(appUser(userId, "batch-user"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "mode", "NORMAL",
                                "filters", Map.of("subjectId", subjectId.toString()),
                                "requestedCount", ITEM_COUNT))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(result).path("sessionId").asText());
    }

    private JsonNode readJson(final MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @FunctionalInterface
    private interface RequestAction {
        void run() throws Exception;
    }
}
