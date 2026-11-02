package com.certpractice.aigateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * AI generation batches, candidate validation/conversion and admin
 * configuration (T125-T126, T137-T138).
 */
class AiGenerationIntegrationTest extends P3TestSupport {

    @Autowired
    private AiGenerationScheduler scheduler;

    @Test
    void generatesValidatedCandidatesFromAnApprovedSourceQuestion() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        UUID tagId = createTag("generated");
        QuestionRef source = createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                "generation source " + UUID.randomUUID(), "A", List.of(tagId));

        JsonNode created = createBatch(catalog.subjectId(), catalog.chapterId(),
                List.of(source.questionId()), 4, "MEDIUM", 201);
        UUID batchId = UUID.fromString(created.path("batchId").asText());
        assertThat(created.path("status").asText()).isEqualTo("CREATED");

        assertThat(scheduler.runOnce()).isTrue();

        JsonNode detail = getAdminJson("/api/v1/admin/ai/generation-batches/" + batchId);
        assertThat(detail.path("status").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(detail.path("attemptCount").asInt()).isEqualTo(1);
        assertThat(detail.path("candidates")).hasSize(2);
        JsonNode candidate = detail.path("candidates").get(0);
        assertThat(candidate.path("validationStatus").asText()).isEqualTo("VALID");
        assertThat(candidate.path("reviewStatus").asText()).isEqualTo("PENDING");
        assertThat(candidate.path("candidate").path("stem").asText()).contains("Generated variant");
        assertThat(candidate.path("candidate").path("options")).hasSize(2);

        JsonNode sentToPython = AI_SERVER.lastGenerateRequest();
        assertThat(sentToPython.path("sourceQuestionVersionId").asText())
                .isEqualTo(source.versionId().toString());
        assertThat(sentToPython.path("stem").asText()).contains("generation source");
        assertThat(sentToPython.path("type").asText()).isEqualTo("SINGLE_CHOICE");
        assertThat(sentToPython.path("targetCount").asInt()).isEqualTo(4);
        assertThat(sentToPython.path("constraints").path("difficulty").asText())
                .isEqualTo("MEDIUM");
        JsonNode tags = sentToPython.path("tags");
        assertThat(tags.isArray()).isTrue();
        assertThat(tags.toString()).contains("generated");
    }

    @Test
    void rejectsDuplicateCandidatesWithinOneBatch() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        QuestionRef source = createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                "duplicate source " + UUID.randomUUID(), "A", List.of());
        String duplicate = """
                [{"stem":"duplicate variant","type":"SINGLE_CHOICE",
                  "options":[{"key":"A","content":"A","isCorrect":true},
                             {"key":"B","content":"B","isCorrect":false}],
                  "referenceAnswer":null,"explanation":"Same content","difficulty":"MEDIUM",
                  "suggestedTags":[]},
                 {"stem":"duplicate variant","type":"SINGLE_CHOICE",
                  "options":[{"key":"A","content":"A","isCorrect":true},
                             {"key":"B","content":"B","isCorrect":false}],
                  "referenceAnswer":null,"explanation":"Same content","difficulty":"MEDIUM",
                  "suggestedTags":[]}]
                """;
        AI_SERVER.respondGenerate(duplicate);

        JsonNode created = createBatch(catalog.subjectId(), catalog.chapterId(),
                List.of(source.questionId()), 2, null, 201);
        scheduler.runOnce();

        JsonNode detail = getAdminJson("/api/v1/admin/ai/generation-batches/"
                + created.path("batchId").asText());
        assertThat(detail.path("candidates")).hasSize(1);
        assertThat(detail.path("candidates").get(0).path("validationStatus").asText())
                .isEqualTo("VALID");
    }

    @Test
    void marksStructurallyInvalidCandidatesAndPreventsReview() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        QuestionRef source = createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                "invalid source " + UUID.randomUUID(), "A", List.of());
        String invalid = """
                [{"stem":"invalid variant","type":"SINGLE_CHOICE",
                  "options":[{"key":"A","content":"A","isCorrect":false},
                             {"key":"B","content":"B","isCorrect":false}],
                  "referenceAnswer":null,"explanation":"No correct option","difficulty":"MEDIUM",
                  "suggestedTags":[]}]
                """;
        AI_SERVER.respondGenerate(invalid);

        JsonNode created = createBatch(catalog.subjectId(), catalog.chapterId(),
                List.of(source.questionId()), 1, null, 201);
        scheduler.runOnce();

        JsonNode detail = getAdminJson("/api/v1/admin/ai/generation-batches/"
                + created.path("batchId").asText());
        JsonNode candidate = detail.path("candidates").get(0);
        assertThat(candidate.path("validationStatus").asText()).isEqualTo("INVALID");
        assertThat(candidate.path("reviewStatus").asText()).isEqualTo("REJECTED");

        reviewCandidate(UUID.fromString(candidate.path("candidateId").asText()),
                "APPROVE", 409);
    }

    @Test
    void rejectsCandidatesWhoseStemAlreadyExists() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        String stem = "existing stem " + UUID.randomUUID();
        QuestionRef source = createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                stem, "A", List.of());
        String duplicateStem = """
                [{"stem":"%s","type":"SINGLE_CHOICE",
                  "options":[{"key":"A","content":"A","isCorrect":true},
                             {"key":"B","content":"B","isCorrect":false}],
                  "referenceAnswer":null,"explanation":"Duplicate stem","difficulty":"MEDIUM",
                  "suggestedTags":[]}]
                """.formatted(stem);
        AI_SERVER.respondGenerate(duplicateStem);

        JsonNode created = createBatch(catalog.subjectId(), catalog.chapterId(),
                List.of(source.questionId()), 1, null, 201);
        scheduler.runOnce();

        JsonNode detail = getAdminJson("/api/v1/admin/ai/generation-batches/"
                + created.path("batchId").asText());
        assertThat(detail.path("candidates").get(0).path("validationStatus").asText())
                .isEqualTo("INVALID");
    }

    @Test
    void approvingACandidatePublishesAnImmutableVariantQuestion() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        UUID tagId = createTag("approved-tag");
        QuestionRef source = createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                "approval source " + UUID.randomUUID(), "A", List.of(tagId));
        String valid = """
                [{"stem":"approval variant %s","type":"SINGLE_CHOICE",
                  "options":[{"key":"A","content":"Approved A","isCorrect":true},
                             {"key":"B","content":"Approved B","isCorrect":false}],
                  "referenceAnswer":null,"explanation":"Approved explanation","difficulty":"EASY",
                  "suggestedTags":["approved-tag"]}]
                """.formatted(UUID.randomUUID());
        AI_SERVER.respondGenerate(valid);

        JsonNode created = createBatch(catalog.subjectId(), catalog.chapterId(),
                List.of(source.questionId()), 1, null, 201);
        scheduler.runOnce();
        JsonNode detail = getAdminJson("/api/v1/admin/ai/generation-batches/"
                + created.path("batchId").asText());
        UUID candidateId = UUID.fromString(
                detail.path("candidates").get(0).path("candidateId").asText());

        JsonNode reviewed = reviewCandidate(candidateId, "APPROVE", 200);
        assertThat(reviewed.path("reviewStatus").asText()).isEqualTo("APPROVED");
        UUID convertedVersionId = UUID.fromString(
                reviewed.path("convertedQuestionVersionId").asText());

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM question_version WHERE id = ?",
                String.class,
                convertedVersionId);
        assertThat(status).isEqualTo("PUBLISHED");
        UUID publishedVersionId = jdbcTemplate.queryForObject("""
                SELECT q.published_version_id FROM question q
                JOIN question_version v ON v.question_id = q.id
                WHERE v.id = ?
                """, UUID.class, convertedVersionId);
        assertThat(publishedVersionId).isEqualTo(convertedVersionId);
        Integer correctOptions = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM question_option
                WHERE question_version_id = ? AND is_correct = true
                """, Integer.class, convertedVersionId);
        assertThat(correctOptions).isEqualTo(1);
        Integer sources = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM ai_variant_source
                WHERE question_version_id = ? AND source_question_version_id = ?
                """, Integer.class, convertedVersionId, source.versionId());
        assertThat(sources).isEqualTo(1);
        Integer linkedTags = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM question_version_tag WHERE question_version_id = ?
                """, Integer.class, convertedVersionId);
        assertThat(linkedTags).isEqualTo(1);

        reviewCandidate(candidateId, "APPROVE", 409);
    }

    @Test
    void rejectingACandidateDoesNotCreateAQuestion() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        QuestionRef source = createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                "reject source " + UUID.randomUUID(), "A", List.of());
        JsonNode created = createBatch(catalog.subjectId(), catalog.chapterId(),
                List.of(source.questionId()), 1, null, 201);
        scheduler.runOnce();
        JsonNode detail = getAdminJson("/api/v1/admin/ai/generation-batches/"
                + created.path("batchId").asText());
        UUID candidateId = UUID.fromString(
                detail.path("candidates").get(0).path("candidateId").asText());
        int questionsBefore = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM question", Integer.class);

        JsonNode reviewed = reviewCandidate(candidateId, "REJECT", 200);
        assertThat(reviewed.path("reviewStatus").asText()).isEqualTo("REJECTED");
        assertThat(reviewed.hasNonNull("convertedQuestionVersionId")).isFalse();
        int questionsAfter = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM question", Integer.class);
        assertThat(questionsAfter).isEqualTo(questionsBefore);

        reviewCandidate(candidateId, "APPROVE", 409);
    }

    @Test
    void validatesBatchCreationInputAndAdminRole() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        QuestionRef source = createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                "validation source " + UUID.randomUUID(), "A", List.of());
        UUID userId = registerUser();

        createBatch(catalog.subjectId(), catalog.chapterId(), List.of(source.questionId()),
                0, null, 400);
        createBatch(catalog.subjectId(), catalog.chapterId(), List.of(), 10, null, 400);

        Map<String, Object> payload = batchPayload(catalog.subjectId(), catalog.chapterId(),
                List.of(source.questionId()), 10, null);
        mockMvc.perform(post("/api/v1/admin/ai/generation-batches")
                        .with(appUser(userId, usernameFor(userId)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminConfigCreatesNewVersionsAndReturnsTheLatest() throws Exception {
        Map<String, Object> firstValue = Map.of(
                "dailyRequestLimit", 8,
                "sessionTurnLimit", 15,
                "teamDailyBudget", 2.5,
                "reservedCost", 0.002,
                "maxInputChars", 1500,
                "sendOfficialAnswer", false);
        JsonNode first = updateConfig(firstValue);
        assertThat(first.path("key").asText()).isEqualTo("ai.settings");
        assertThat(first.path("versionNo").asInt()).isEqualTo(1);
        assertThat(first.path("value").path("dailyRequestLimit").asInt()).isEqualTo(8);

        Map<String, Object> secondValue = new HashMap<>(firstValue);
        secondValue.put("dailyRequestLimit", 12);
        JsonNode second = updateConfig(secondValue);
        assertThat(second.path("versionNo").asInt()).isEqualTo(2);

        JsonNode latest = getAdminJson("/api/v1/admin/configs?key=ai.settings");
        assertThat(latest.path("versionNo").asInt()).isEqualTo(2);
        assertThat(latest.path("value").path("dailyRequestLimit").asInt()).isEqualTo(12);

        Integer versions = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM system_config_version WHERE config_key = 'ai.settings'
                """, Integer.class);
        assertThat(versions).isEqualTo(2);
    }

    @Test
    void rejectsInvalidAdminConfigValues() throws Exception {
        Map<String, Object> invalid = Map.of(
                "dailyRequestLimit", 0,
                "sessionTurnLimit", 15,
                "teamDailyBudget", 2.5,
                "reservedCost", 0.002,
                "maxInputChars", 1500,
                "sendOfficialAnswer", false);
        mockMvc.perform(put("/api/v1/admin/configs")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("key", "ai.settings", "value", invalid))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private JsonNode createBatch(
            final UUID subjectId,
            final UUID chapterId,
            final List<UUID> sourceQuestionIds,
            final int targetCount,
            final String difficulty,
            final int expectedStatus) throws Exception {
        Map<String, Object> payload = batchPayload(subjectId, chapterId,
                sourceQuestionIds, targetCount, difficulty);
        MvcResult result = mockMvc.perform(post("/api/v1/admin/ai/generation-batches")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return readJson(result);
    }

    private Map<String, Object> batchPayload(
            final UUID subjectId,
            final UUID chapterId,
            final List<UUID> sourceQuestionIds,
            final int targetCount,
            final String difficulty) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("subjectId", subjectId.toString());
        payload.put("chapterId", chapterId == null ? null : chapterId.toString());
        payload.put("sourceQuestionIds",
                sourceQuestionIds.stream().map(UUID::toString).toList());
        payload.put("targetCount", targetCount);
        if (difficulty != null) {
            payload.put("difficulty", difficulty);
        }
        return payload;
    }

    private JsonNode reviewCandidate(
            final UUID candidateId,
            final String decision,
            final int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/api/v1/admin/ai/generation-candidates/{id}/review", candidateId)
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("decision", decision))))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return readJson(result);
    }

    private JsonNode updateConfig(final Map<String, Object> values) throws Exception {
        MvcResult result = mockMvc.perform(put("/api/v1/admin/configs")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("key", "ai.settings", "value", values))))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result);
    }
}
