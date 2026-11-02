package com.certpractice.aigateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.certpractice.practice.PracticeSession;
import com.certpractice.practice.SessionItem;
import com.certpractice.support.P3TestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * AI question gating, context minimization and conversation ownership (AC-10).
 */
class AiGateIntegrationTest extends P3TestSupport {

    @Test
    void rejectsQuestionConversationsForInProgressItems() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        QuestionRef question = createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                "gate in progress " + UUID.randomUUID(), "A", List.of());
        UUID userId = registerUser();
        markSessionStatus(userId, question, PracticeSession.Status.IN_PROGRESS);
        UUID itemId = onlyItemId(userId);

        createConversation(userId, "QUESTION", itemId, 409);
        assertThat(conversationCount(userId)).isZero();
        assertThat(AI_SERVER.lastChatRequest()).isNull();
    }

    @Test
    void rejectsQuestionConversationsWhileSelfAssessmentIsPending() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        QuestionRef question = createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                "gate pending " + UUID.randomUUID(), "A", List.of());
        UUID userId = registerUser();
        markSessionStatus(userId, question, PracticeSession.Status.PENDING_SELF_ASSESSMENT);
        UUID itemId = onlyItemId(userId);

        createConversation(userId, "QUESTION", itemId, 409);
        assertThat(conversationCount(userId)).isZero();
    }

    @Test
    void allowsTheOwnerAfterCompletionAndSendsMinimalContext() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        QuestionRef question = createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                "gate completed " + UUID.randomUUID(), "A", List.of());
        UUID userId = registerUser();
        createCompletedItem(userId, question, SessionItem.Result.PASS);
        UUID itemId = onlyItemId(userId);

        JsonNode conversation = createConversation(userId, "QUESTION", itemId, 201);
        UUID conversationId = UUID.fromString(conversation.path("conversationId").asText());
        assertThat(conversation.path("scope").asText()).isEqualTo("QUESTION");
        assertThat(conversation.path("questionId").asText())
                .isEqualTo(question.questionId().toString());

        MvcResult sent = sendMessage(userId, conversationId,
                "为什么这个选项正确？", "chat-key-1", 200);
        JsonNode response = readJson(sent);
        assertThat(response.path("assistantMessage").path("content").asText())
                .isEqualTo("Mock AI answer");
        assertThat(response.path("usage").path("inputTokens").asInt()).isEqualTo(3);
        assertThat(response.path("usage").path("outputTokens").asInt()).isEqualTo(2);
        assertThat(response.path("quota").path("used").asInt()).isEqualTo(1);
        assertThat(response.path("quota").path("remaining").asInt()).isEqualTo(9);

        JsonNode sentToPython = AI_SERVER.lastChatRequest();
        assertThat(sentToPython).isNotNull();
        JsonNode context = sentToPython.path("context");
        assertThat(context.path("scope").asText()).isEqualTo("QUESTION");
        assertThat(context.path("stem").asText()).contains("gate completed");
        assertThat(context.path("questionType").asText()).isEqualTo("SINGLE_CHOICE");
        assertThat(context.hasNonNull("userAnswer")).isTrue();
        assertThat(context.path("selfAssessment").asText()).isEqualTo("PASS");
        assertThat(context.hasNonNull("explanation")).isFalse();
        assertThat(context.hasNonNull("referenceAnswer")).isFalse();
        assertThat(sentToPython.toString()).doesNotContain("Explanation for gate completed");

        JsonNode stored = getJson("/api/v1/ai/conversations/" + conversationId, userId);
        assertThat(stored.path("messages")).hasSize(2);
        assertThat(stored.path("messages").get(0).path("role").asText()).isEqualTo("USER");
        assertThat(stored.path("messages").get(1).path("role").asText()).isEqualTo("ASSISTANT");
    }

    @Test
    void generalConversationsDoNotCarryQuestionContext() throws Exception {
        UUID userId = registerUser();
        UUID conversationId = createGeneralConversation(userId);

        sendMessage(userId, conversationId, "请解释一下错题复习策略", "general-key-1", 200);

        JsonNode context = AI_SERVER.lastChatRequest().path("context");
        assertThat(context.path("scope").asText()).isEqualTo("GENERAL");
        assertThat(context.hasNonNull("stem")).isFalse();
        assertThat(context.hasNonNull("userAnswer")).isFalse();
    }

    @Test
    void clearingAConversationKeepsCostRecordsAndReturnsNoMessages() throws Exception {
        UUID userId = registerUser();
        UUID conversationId = createGeneralConversation(userId);
        sendMessage(userId, conversationId, "保留成本记录", "clear-key-1", 200);

        mockMvc.perform(delete("/api/v1/ai/conversations/{id}", conversationId)
                        .with(appUser(userId, usernameFor(userId)))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        JsonNode cleared = getJson("/api/v1/ai/conversations/" + conversationId, userId);
        assertThat(cleared.path("messages")).isEmpty();
        assertThat(cleared.path("status").asText()).isEqualTo("CLEARED");
        Integer requests = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ai_request WHERE conversation_id = ?",
                Integer.class,
                conversationId);
        assertThat(requests).isEqualTo(1);
    }

    @Test
    void otherUsersCannotReadOrClearAConversation() throws Exception {
        UUID owner = registerUser();
        UUID other = registerUser();
        UUID conversationId = createGeneralConversation(owner);
        sendMessage(owner, conversationId, "私有会话", "private-key-1", 200);

        mockMvc.perform(get("/api/v1/ai/conversations/{id}", conversationId)
                        .with(appUser(other, usernameFor(other))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/ai/conversations/{id}", conversationId)
                        .with(appUser(other, usernameFor(other)))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void otherUsersCannotOpenAQuestionConversationForSomeoneElsesCompletedItem() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        QuestionRef question = createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                "gate ownership " + UUID.randomUUID(), "A", List.of());
        UUID owner = registerUser();
        createCompletedItem(owner, question, SessionItem.Result.PASS);
        UUID itemId = onlyItemId(owner);
        UUID other = registerUser();

        createConversation(other, "QUESTION", itemId, 404);
        assertThat(conversationCount(other)).isZero();
    }

    private JsonNode createConversation(
            final UUID userId,
            final String scope,
            final UUID sessionItemId,
            final int expectedStatus) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("scope", scope);
        if (sessionItemId != null) {
            body.put("sessionItemId", sessionItemId.toString());
        }
        MvcResult result = mockMvc.perform(post("/api/v1/ai/conversations")
                        .with(appUser(userId, usernameFor(userId)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return readJson(result);
    }

    private UUID onlyItemId(final UUID userId) {
        return jdbcTemplate.queryForObject("""
                SELECT si.id
                FROM session_item si
                JOIN practice_session ps ON ps.id = si.session_id
                WHERE ps.user_id = ?
                ORDER BY si.ordinal
                LIMIT 1
                """, UUID.class, userId);
    }

    private int conversationCount(final UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ai_conversation WHERE user_id = ?",
                Integer.class,
                userId);
        return count == null ? 0 : count;
    }
}
