package com.certpractice.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.certpractice.support.P1TestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class PracticeIntegrationTest extends P1TestSupport {

    @Test
    void sessionCreationIsIdempotentDeduplicatedAndAnswerSafe() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                "choice one " + UUID.randomUUID(), "B", List.of());
        createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                "choice two " + UUID.randomUUID(), "C", List.of());
        createPublishedBlank(catalog.subjectId(), catalog.chapterId(),
                "blank one " + UUID.randomUUID(), "SYN", List.of("同步"), List.of());
        createPublishedBlank(catalog.subjectId(), catalog.chapterId(),
                "blank two " + UUID.randomUUID(), "80", List.of(), List.of());
        UUID userId = registerUser();

        UUID firstSession = createSession(userId, "user", catalog.subjectId(), 10, "create-key-1");
        UUID replaySession = createSession(userId, "user", catalog.subjectId(), 10, "create-key-1");
        assertThat(replaySession).isEqualTo(firstSession);

        Map<String, Object> conflicting = Map.of(
                "mode", "NORMAL",
                "filters", Map.of("subjectId", catalog.subjectId().toString(), "historyDays", 90),
                "requestedCount", 5);
        mockMvc.perform(post("/api/v1/practice-sessions")
                        .with(appUser(userId, "user"))
                        .with(csrf())
                        .header("Idempotency-Key", "create-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflicting)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_MISMATCH"));

        MvcResult sessionResult = mockMvc.perform(get("/api/v1/practice-sessions/{id}", firstSession)
                        .with(appUser(userId, "user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualCount").value(4))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andReturn();
        String raw = sessionResult.getResponse().getContentAsString();
        assertThat(raw).doesNotContain("isCorrect", "correctOptionId", "referenceAnswer",
                "explanation", "aliases");

        JsonNode session = objectMapper.readTree(raw);
        Set<String> questionIds = new HashSet<>();
        for (JsonNode item : session.path("items")) {
            questionIds.add(item.path("question").path("questionId").asText());
        }
        assertThat(questionIds).hasSize(4);
    }

    @Test
    void submissionRequiresAllAnswersAndCannotBeRepeated() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                "submit choice " + UUID.randomUUID(), "B", List.of());
        createPublishedBlank(catalog.subjectId(), catalog.chapterId(),
                "submit blank one " + UUID.randomUUID(), "SYN", List.of("同步"), List.of());
        createPublishedBlank(catalog.subjectId(), catalog.chapterId(),
                "submit blank two " + UUID.randomUUID(), "80", List.of(), List.of());
        UUID userId = registerUser();
        UUID sessionId = createSession(userId, "user", catalog.subjectId(), 10, null);

        JsonNode session = getSession(userId, sessionId);
        JsonNode choice = firstItemOfType(session, "SINGLE_CHOICE");
        List<JsonNode> blanks = itemsOfType(session, "SINGLE_BLANK");
        assertThat(blanks).hasSize(2);

        UUID choiceItemId = UUID.fromString(choice.path("itemId").asText());
        UUID correctOptionId = optionId(choice, "B");
        saveChoiceAnswer(userId, sessionId, choiceItemId, correctOptionId, 0, 200);
        saveChoiceAnswer(userId, sessionId, choiceItemId, correctOptionId, 0, 409);

        mockMvc.perform(post("/api/v1/practice-sessions/{id}/submit", sessionId)
                        .with(appUser(userId, "user"))
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ANSWERS_INCOMPLETE"));

        for (JsonNode blank : blanks) {
            UUID itemId = UUID.fromString(blank.path("itemId").asText());
            saveBlankAnswer(userId, sessionId, itemId, "SYN", 0);
        }

        JsonNode submitted = submit(userId, sessionId, "submit-key-1", 200);
        assertThat(submitted.path("status").asText()).isEqualTo("PENDING_SELF_ASSESSMENT");
        assertThat(submitted.path("objective").path("answered").asInt()).isEqualTo(1);
        assertThat(submitted.path("objective").path("correct").asInt()).isEqualTo(1);
        assertThat(submitted.path("items").toString()).contains("correctOptionId",
                "referenceAnswer", "explanation");

        saveChoiceAnswer(userId, sessionId, choiceItemId, correctOptionId, 1, 409);

        JsonNode replay = submit(userId, sessionId, "submit-key-1", 200);
        assertThat(replay.path("status").asText()).isEqualTo(submitted.path("status").asText());
        assertThat(replay.path("objective").path("correct").asInt())
                .isEqualTo(submitted.path("objective").path("correct").asInt());

        JsonNode firstAssessment = selfAssess(userId, sessionId, blanks.get(0), "PASS", 200);
        assertThat(firstAssessment.path("sessionStatus").asText()).isEqualTo("PENDING_SELF_ASSESSMENT");
        JsonNode finalAssessment = selfAssess(userId, sessionId, blanks.get(1), "PASS", 200);
        assertThat(finalAssessment.path("sessionStatus").asText()).isEqualTo("COMPLETED");

        selfAssess(userId, sessionId, blanks.get(1), "FAIL", 409);

        mockMvc.perform(get("/api/v1/practice-sessions/{id}/result", sessionId)
                        .with(appUser(userId, "user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.fill.selfPassed").value(2))
                .andExpect(jsonPath("$.fill.selfFailed").value(0));
    }

    @Test
    void wrongQuestionsFavoritesAndOwnershipFollowP1Rules() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        QuestionRef choice = createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                "wrong choice " + UUID.randomUUID(), "B", List.of());
        QuestionRef blank = createPublishedBlank(catalog.subjectId(), catalog.chapterId(),
                "wrong blank " + UUID.randomUUID(), "SYN", List.of(), List.of());
        UUID userId = registerUser();
        UUID sessionId = createSession(userId, "user", catalog.subjectId(), 10, null);

        JsonNode session = getSession(userId, sessionId);
        JsonNode choiceItem = firstItemOfType(session, "SINGLE_CHOICE");
        JsonNode blankItem = firstItemOfType(session, "SINGLE_BLANK");
        saveChoiceAnswer(userId, sessionId,
                UUID.fromString(choiceItem.path("itemId").asText()),
                optionId(choiceItem, "A"), 0, 200);
        saveBlankAnswer(userId, sessionId,
                UUID.fromString(blankItem.path("itemId").asText()), "wrong", 0);
        submit(userId, sessionId, "wrong-submit-1", 200);
        selfAssess(userId, sessionId, blankItem, "FAIL", 200);

        JsonNode wrongList = getJson("/api/v1/me/wrong-questions", userId);
        assertThat(wrongList.size()).isEqualTo(2);

        mockMvc.perform(delete("/api/v1/me/wrong-questions/{questionId}", choice.questionId())
                        .with(appUser(userId, "user")).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(getJson("/api/v1/me/wrong-questions", userId).size()).isEqualTo(1);

        UUID secondSession = createSession(userId, "user", catalog.subjectId(), 10, null);
        JsonNode secondView = getSession(userId, secondSession);
        JsonNode secondChoice = firstItemOfType(secondView, "SINGLE_CHOICE");
        saveChoiceAnswer(userId, secondSession,
                UUID.fromString(secondChoice.path("itemId").asText()),
                optionId(secondChoice, "A"), 0, 200);
        JsonNode secondBlank = firstItemOfType(secondView, "SINGLE_BLANK");
        saveBlankAnswer(userId, secondSession,
                UUID.fromString(secondBlank.path("itemId").asText()), "wrong", 0);
        submit(userId, secondSession, "wrong-submit-2", 200);
        selfAssess(userId, secondSession, secondBlank, "FAIL", 200);
        assertThat(getJson("/api/v1/me/wrong-questions", userId).size()).isEqualTo(2);

        mockMvc.perform(put("/api/v1/me/favorites/{questionId}", choice.questionId())
                        .with(appUser(userId, "user")).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/v1/me/favorites/{questionId}", choice.questionId())
                        .with(appUser(userId, "user")).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(getJson("/api/v1/me/favorites", userId).size()).isEqualTo(1);
        mockMvc.perform(delete("/api/v1/me/favorites/{questionId}", choice.questionId())
                        .with(appUser(userId, "user")).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/me/favorites/{questionId}", choice.questionId())
                        .with(appUser(userId, "user")).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(getJson("/api/v1/me/favorites", userId).size()).isZero();

        JsonNode records = getJson("/api/v1/me/records?size=100", userId);
        assertThat(records.path("total").asInt()).isGreaterThanOrEqualTo(2);
        mockMvc.perform(get("/api/v1/me/records/{id}", sessionId)
                        .with(appUser(userId, "user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].questionVersionId").exists());

        UUID otherUser = registerUser();
        mockMvc.perform(get("/api/v1/me/records/{id}", sessionId)
                        .with(appUser(otherUser, "other")))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchUsesTagIntersectionAndNeverHiddenFields() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        String unique = UUID.randomUUID().toString().substring(0, 8);
        UUID tagA = createTag("tag-a-" + unique);
        UUID tagB = createTag("tag-b-" + unique);
        String stemOne = "search alpha " + unique;
        String stemTwo = "search beta " + unique;
        createPublishedChoice(catalog.subjectId(), catalog.chapterId(), stemOne, "B", List.of(tagA));
        createPublishedChoice(catalog.subjectId(), catalog.chapterId(), stemTwo, "C", List.of(tagA, tagB));
        UUID userId = registerUser();

        JsonNode intersection = search(userId, catalog.subjectId(), null,
                tagA.toString() + "," + tagB.toString());
        assertThat(intersection.path("total").asInt()).isEqualTo(1);
        assertThat(intersection.path("items").get(0).path("stem").asText()).isEqualTo(stemTwo);

        JsonNode optionKeyword = search(userId, catalog.subjectId(),
                "B option for " + stemTwo, null);
        assertThat(optionKeyword.path("total").asInt()).isZero();

        JsonNode explanationKeyword = search(userId, catalog.subjectId(),
                "Explanation for " + stemTwo, null);
        assertThat(explanationKeyword.path("total").asInt()).isZero();

        JsonNode stemKeyword = search(userId, catalog.subjectId(), stemTwo, null);
        assertThat(stemKeyword.path("total").asInt()).isEqualTo(1);

        JsonNode tagKeyword = search(userId, catalog.subjectId(), "tag-b-" + unique, null);
        assertThat(tagKeyword.path("total").asInt()).isEqualTo(1);

        String raw = objectMapper.writeValueAsString(intersection);
        assertThat(raw).doesNotContain("isCorrect", "referenceAnswer", "explanation", "aliases");
    }

    @Test
    void offlineQuestionIsExcludedFromNewSessionsButHistoryRemains() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        QuestionRef first = createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                "offline one " + UUID.randomUUID(), "B", List.of());
        createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                "offline two " + UUID.randomUUID(), "B", List.of());
        UUID userId = registerUser();

        UUID sessionId = createSession(userId, "user", catalog.subjectId(), 10, null);
        JsonNode session = getSession(userId, sessionId);
        assertThat(session.path("actualCount").asInt()).isEqualTo(2);
        for (JsonNode item : session.path("items")) {
            if (item.path("question").path("type").asText().equals("SINGLE_CHOICE")) {
                saveChoiceAnswer(userId, sessionId,
                        UUID.fromString(item.path("itemId").asText()),
                        optionId(item, "B"), 0, 200);
            }
        }
        submit(userId, sessionId, "offline-submit", 200);

        mockMvc.perform(post("/api/v1/admin/questions/{id}/offline", first.questionId())
                        .with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFFLINE"));

        mockMvc.perform(get("/api/v1/questions/{id}", first.questionId())
                        .with(appUser(userId, "user")))
                .andExpect(status().isNotFound());

        JsonNode search = search(userId, catalog.subjectId(), null, null);
        assertThat(search.path("total").asInt()).isEqualTo(1);

        UUID newSession = createSession(userId, "user", catalog.subjectId(), 10, null);
        JsonNode newView = getSession(userId, newSession);
        assertThat(newView.path("actualCount").asInt()).isEqualTo(1);
        assertThat(newView.path("items").toString()).doesNotContain(first.questionId().toString());

        mockMvc.perform(get("/api/v1/me/records/{id}", sessionId)
                        .with(appUser(userId, "user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    private UUID createSession(
            final UUID userId,
            final String username,
            final UUID subjectId,
            final int requestedCount,
            final String idempotencyKey) throws Exception {
        Map<String, Object> body = Map.of(
                "mode", "NORMAL",
                "filters", Map.of("subjectId", subjectId.toString(), "historyDays", 90),
                "requestedCount", requestedCount);
        MockHttpServletRequestBuilder request = post("/api/v1/practice-sessions")
                .with(appUser(userId, username))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(result).path("sessionId").asText());
    }

    private JsonNode getSession(final UUID userId, final UUID sessionId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/practice-sessions/{id}", sessionId)
                        .with(appUser(userId, "user")))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result);
    }

    private void saveChoiceAnswer(
            final UUID userId,
            final UUID sessionId,
            final UUID itemId,
            final UUID optionId,
            final int revision,
            final int expectedStatus) throws Exception {
        Map<String, Object> body = Map.of(
                "answerRevision", revision,
                "answer", Map.of("selectedOptionId", optionId.toString()));
        mockMvc.perform(put("/api/v1/practice-sessions/{id}/items/{itemId}/answer", sessionId, itemId)
                        .with(appUser(userId, "user"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus));
    }

    private void saveBlankAnswer(
            final UUID userId,
            final UUID sessionId,
            final UUID itemId,
            final String text,
            final int revision) throws Exception {
        Map<String, Object> body = Map.of(
                "answerRevision", revision,
                "answer", Map.of("text", text));
        mockMvc.perform(put("/api/v1/practice-sessions/{id}/items/{itemId}/answer", sessionId, itemId)
                        .with(appUser(userId, "user"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    private JsonNode submit(
            final UUID userId,
            final UUID sessionId,
            final String idempotencyKey,
            final int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder request = post(
                "/api/v1/practice-sessions/{id}/submit", sessionId)
                .with(appUser(userId, "user"))
                .with(csrf());
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return readJson(result);
    }

    private JsonNode selfAssess(
            final UUID userId,
            final UUID sessionId,
            final JsonNode item,
            final String result,
            final int expectedStatus) throws Exception {
        UUID itemId = UUID.fromString(item.path("itemId").asText());
        MvcResult response = mockMvc.perform(post(
                        "/api/v1/practice-sessions/{id}/items/{itemId}/self-assessment",
                        sessionId, itemId)
                        .with(appUser(userId, "user"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("result", result))))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return readJson(response);
    }

    private JsonNode search(
            final UUID userId,
            final UUID subjectId,
            final String keyword,
            final String tagIds) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/questions/search")
                .with(appUser(userId, "user"))
                .param("subjectId", subjectId.toString())
                .param("size", "100");
        if (keyword != null) {
            request.param("keyword", keyword);
        }
        if (tagIds != null) {
            request.param("tagIds", tagIds);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result);
    }

    private JsonNode getJson(final String path, final UUID userId) throws Exception {
        MvcResult result = mockMvc.perform(get(path).with(appUser(userId, "user")))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result);
    }

    private JsonNode firstItemOfType(final JsonNode session, final String type) {
        List<JsonNode> items = itemsOfType(session, type);
        assertThat(items).isNotEmpty();
        return items.get(0);
    }

    private List<JsonNode> itemsOfType(final JsonNode session, final String type) {
        List<JsonNode> items = new ArrayList<>();
        for (JsonNode item : session.path("items")) {
            if (type.equals(item.path("question").path("type").asText())) {
                items.add(item);
            }
        }
        return items;
    }

    private UUID optionId(final JsonNode item, final String key) {
        for (JsonNode option : item.path("question").path("options")) {
            if (key.equals(option.path("key").asText())) {
                return UUID.fromString(option.path("id").asText());
            }
        }
        throw new AssertionError("Option key not found: " + key);
    }

    private JsonNode readJson(final MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
