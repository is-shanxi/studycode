package com.certpractice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.certpractice.support.P1TestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class P1RedactionTest extends P1TestSupport {

    @Test
    void passwordsAnswersAndExplanationsNeverReachLogsOrProblemResponses() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);

        try {
            String password = "never-log-this-password";
            MvcResult login = mockMvc.perform(post("/api/v1/auth/sessions")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "username", "student01", "password", password))))
                    .andExpect(status().isUnauthorized())
                    .andReturn();
            assertThat(login.getResponse().getContentAsString()).doesNotContain(password);

            SubjectChapter catalog = createSubjectAndChapter();
            QuestionRef blank = createPublishedBlank(catalog.subjectId(), catalog.chapterId(),
                    "redaction stem " + UUID.randomUUID(), "SYN", List.of(), List.of());
            createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                    "redaction choice " + UUID.randomUUID(), "B", List.of());
            UUID userId = registerUser();
            UUID sessionId = createSession(userId, catalog.subjectId());

            MvcResult sessionResult = mockMvc.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                    .get("/api/v1/practice-sessions/{id}", sessionId)
                                    .with(appUser(userId, "user")))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode session = objectMapper.readTree(sessionResult.getResponse().getContentAsString());
            JsonNode item = null;
            for (JsonNode candidate : session.path("items")) {
                if ("SINGLE_BLANK".equals(candidate.path("question").path("type").asText())) {
                    item = candidate;
                }
            }
            assertThat(item).isNotNull();
            UUID itemId = UUID.fromString(item.path("itemId").asText());
            String answerText = "secret-answer-" + UUID.randomUUID();
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .put("/api/v1/practice-sessions/{id}/items/{itemId}/answer",
                                    sessionId, itemId)
                            .with(appUser(userId, "user"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "answerRevision", 0,
                                    "answer", Map.of("text", answerText)))))
                    .andExpect(status().isOk());

            MvcResult incomplete = mockMvc.perform(post(
                            "/api/v1/practice-sessions/{id}/submit", sessionId)
                            .with(appUser(userId, "user"))
                            .with(csrf()))
                    .andExpect(status().isUnprocessableEntity())
                    .andReturn();
            String problemBody = incomplete.getResponse().getContentAsString();
            assertThat(problemBody).doesNotContain(
                    "isCorrect", "correctOptionId", "referenceAnswer", "explanation", "aliases");

            List<String> messages = new ArrayList<>();
            for (ILoggingEvent event : appender.list) {
                messages.add(event.getFormattedMessage());
            }
            String allLogs = String.join("\n", messages);
            assertThat(allLogs).doesNotContain(password, answerText, "SYN",
                    "Explanation for redaction", blank.questionId().toString());
            assertThat(allLogs).contains("path=/api/v1/practice-sessions");
        } finally {
            root.detachAppender(appender);
        }
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
        return UUID.fromString(objectMapper.readTree(
                result.getResponse().getContentAsString()).path("sessionId").asText());
    }
}
