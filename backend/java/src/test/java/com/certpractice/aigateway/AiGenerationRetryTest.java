package com.certpractice.aigateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.certpractice.support.P3TestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The database-leased scheduler retries a bounded number of times and then
 * leaves the batch in a terminal FAILED state.
 */
@TestPropertySource(properties = "app.ai.generation.max-attempts=2")
class AiGenerationRetryTest extends P3TestSupport {

    @Autowired
    private AiGenerationScheduler scheduler;

    @Test
    void stopsRetryingAfterTheConfiguredAttemptLimit() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        QuestionRef source = createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                "retry source " + UUID.randomUUID(), "A", List.of());
        AI_SERVER.respondGenerateStatus(500);

        Map<String, Object> payload = new HashMap<>();
        payload.put("subjectId", catalog.subjectId().toString());
        payload.put("chapterId", catalog.chapterId().toString());
        payload.put("sourceQuestionIds", List.of(source.questionId().toString()));
        payload.put("targetCount", 1);
        MvcResult created = mockMvc.perform(post("/api/v1/admin/ai/generation-batches")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID batchId = UUID.fromString(readJson(created).path("batchId").asText());

        assertThat(scheduler.runOnce()).isTrue();
        JsonNode first = getAdminJson("/api/v1/admin/ai/generation-batches/" + batchId);
        assertThat(first.path("status").asText()).isEqualTo("CREATED");
        assertThat(first.path("attemptCount").asInt()).isEqualTo(1);
        assertThat(first.path("lastError").asText()).isNotBlank();

        assertThat(scheduler.runOnce()).isTrue();
        JsonNode second = getAdminJson("/api/v1/admin/ai/generation-batches/" + batchId);
        assertThat(second.path("status").asText()).isEqualTo("FAILED");
        assertThat(second.path("attemptCount").asInt()).isEqualTo(2);
        assertThat(second.path("lastError").asText()).isNotBlank();

        assertThat(scheduler.runOnce()).isFalse();
    }
}
