package com.certpractice.selftest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.certpractice.support.P3TestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Self-test composition, fallback and snapshot behavior (AC-08, AC-09).
 */
class SelfTestIntegrationTest extends P3TestSupport {

    @Test
    void usesSixFourCompositionWhenApprovedVariantsAreSufficient() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        QuestionRef source = createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                "source " + UUID.randomUUID(), "A", List.of());
        for (int index = 0; index < 10; index++) {
            createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                    "original " + index + " " + UUID.randomUUID(), "B", List.of());
        }
        for (int index = 0; index < 4; index++) {
            createApprovedAiVariant(catalog.subjectId(), catalog.chapterId(), source.versionId(),
                    "variant " + index + " " + UUID.randomUUID(), "C");
        }
        UUID userId = registerUser();

        JsonNode session = createSelfTest(userId, catalog.subjectId(), 10, null, null,
                "selftest-complete", 201);

        assertThat(session.path("mode").asText()).isEqualTo("SELF_TEST");
        assertThat(session.path("requestedCount").asInt()).isEqualTo(10);
        assertThat(session.path("actualCount").asInt()).isEqualTo(10);
        assertThat(session.path("composition").path("original").asInt()).isEqualTo(6);
        assertThat(session.path("composition").path("aiVariant").asInt()).isEqualTo(4);
        assertThat(countSourceTypes(session, "ORIGINAL")).isEqualTo(6);
        assertThat(countSourceTypes(session, "AI_VARIANT")).isEqualTo(4);

        JsonNode ruleSnapshot = objectMapper.readTree(jdbcTemplate.queryForObject(
                "SELECT rule_snapshot::text FROM practice_session WHERE id = ?",
                String.class,
                UUID.fromString(session.path("sessionId").asText())));
        assertThat(ruleSnapshot.path("composition").path("originalRatio").asDouble())
                .isEqualTo(0.6);
        assertThat(ruleSnapshot.path("composition").path("aiVariantRatio").asDouble())
                .isEqualTo(0.4);
        assertThat(ruleSnapshot.path("targets").path("original").asInt()).isEqualTo(6);
        assertThat(ruleSnapshot.path("targets").path("aiVariant").asInt()).isEqualTo(4);
        assertThat(ruleSnapshot.path("actual").path("aiVariant").asInt()).isEqualTo(4);

        assertThat(session.toString()).doesNotContain("isCorrect", "correctOptionId",
                "referenceAnswer", "explanation", "aliases");
    }

    @Test
    void fillsVariantShortageWithApprovedOriginals() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        QuestionRef source = createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                "shortage source " + UUID.randomUUID(), "A", List.of());
        for (int index = 0; index < 10; index++) {
            createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                    "shortage original " + index + " " + UUID.randomUUID(), "B", List.of());
        }
        createApprovedAiVariant(catalog.subjectId(), catalog.chapterId(), source.versionId(),
                "shortage variant " + UUID.randomUUID(), "C");
        UUID userId = registerUser();

        JsonNode session = createSelfTest(userId, catalog.subjectId(), 10, null, null,
                "selftest-shortage", 201);

        assertThat(session.path("actualCount").asInt()).isEqualTo(10);
        assertThat(session.path("composition").path("original").asInt()).isEqualTo(9);
        assertThat(session.path("composition").path("aiVariant").asInt()).isEqualTo(1);
    }

    @Test
    void usesActualCountWhenTheLegalPoolIsSmallerThanRequested() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        for (int index = 0; index < 3; index++) {
            createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                    "small pool " + index + " " + UUID.randomUUID(), "B", List.of());
        }
        UUID userId = registerUser();

        JsonNode session = createSelfTest(userId, catalog.subjectId(), 10, null, null,
                "selftest-small", 201);

        assertThat(session.path("requestedCount").asInt()).isEqualTo(10);
        assertThat(session.path("actualCount").asInt()).isEqualTo(3);
        assertThat(session.path("composition").path("original").asInt()).isEqualTo(3);
        assertThat(session.path("composition").path("aiVariant").asInt()).isZero();
    }

    @Test
    void rejectsSelfTestWhenNoLegalQuestionMatches() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        UUID userId = registerUser();

        createSelfTest(userId, catalog.subjectId(), 10, null, null, "selftest-empty", 422);
    }

    @Test
    void excludesUnpublishedOrUnapprovedVariantsFromThePool() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        QuestionRef source = createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                "gate source " + UUID.randomUUID(), "A", List.of());
        for (int index = 0; index < 5; index++) {
            createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                    "gate original " + index + " " + UUID.randomUUID(), "B", List.of());
        }
        QuestionRef unpublished = createUnpublishedAiVariant(catalog.subjectId(), catalog.chapterId(),
                source.versionId(), "unpublished variant " + UUID.randomUUID());
        UUID userId = registerUser();

        JsonNode session = createSelfTest(userId, catalog.subjectId(), 10, null, null,
                "selftest-unpublished", 201);

        assertThat(session.path("actualCount").asInt()).isEqualTo(6);
        assertThat(session.path("composition").path("aiVariant").asInt()).isZero();
        for (JsonNode item : session.path("items")) {
            assertThat(item.path("sourceType").asText()).isEqualTo("ORIGINAL");
            assertThat(item.path("question").path("questionId").asText())
                    .isNotEqualTo(unpublished.questionId().toString());
        }
    }

    @Test
    void keepsTheRuleSnapshotStableAcrossSessionsAndIdempotentReplay() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        QuestionRef source = createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                "snapshot source " + UUID.randomUUID(), "A", List.of());
        for (int index = 0; index < 10; index++) {
            createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                    "snapshot original " + index + " " + UUID.randomUUID(), "B", List.of());
        }
        for (int index = 0; index < 4; index++) {
            createApprovedAiVariant(catalog.subjectId(), catalog.chapterId(), source.versionId(),
                    "snapshot variant " + index + " " + UUID.randomUUID(), "C");
        }
        UUID userId = registerUser();

        JsonNode first = createSelfTest(userId, catalog.subjectId(), 10, 0.6, 0.4,
                "snapshot-key", 201);
        JsonNode replay = createSelfTest(userId, catalog.subjectId(), 10, 0.6, 0.4,
                "snapshot-key", 201);

        assertThat(replay.path("sessionId").asText()).isEqualTo(first.path("sessionId").asText());
        assertThat(replay.path("composition").path("original").asInt()).isEqualTo(6);
        assertThat(replay.path("composition").path("aiVariant").asInt()).isEqualTo(4);

        JsonNode second = createSelfTest(userId, catalog.subjectId(), 10, 0.5, 0.5,
                "snapshot-key-2", 201);
        assertThat(second.path("composition").path("original").asInt()).isEqualTo(6);
        assertThat(second.path("composition").path("aiVariant").asInt()).isEqualTo(4);

        String storedSnapshot = jdbcTemplate.queryForObject(
                "SELECT rule_snapshot::text FROM practice_session WHERE id = ?",
                String.class,
                UUID.fromString(first.path("sessionId").asText()));
        JsonNode ruleSnapshot = objectMapper.readTree(storedSnapshot);
        assertThat(ruleSnapshot.path("composition").path("originalRatio").asDouble())
                .isEqualTo(0.6);

        MvcResult reread = mockMvc.perform(get("/api/v1/practice-sessions/{id}",
                        first.path("sessionId").asText())
                        .with(appUser(userId, usernameFor(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.composition.original").value(6))
                .andExpect(jsonPath("$.composition.aiVariant").value(4))
                .andReturn();
        assertThat(readJson(reread).path("sessionId").asText())
                .isEqualTo(first.path("sessionId").asText());
    }

    @Test
    void rejectsInvalidCompositionRatios() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                "ratio question " + UUID.randomUUID(), "A", List.of());
        UUID userId = registerUser();

        createSelfTest(userId, catalog.subjectId(), 10, 0.0, 0.0, "ratio-zero", 400);
        createSelfTest(userId, catalog.subjectId(), 10, -0.1, 1.1, "ratio-negative", 400);
    }

    @Test
    void differentPayloadWithTheSameIdempotencyKeyIsRejected() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        for (int index = 0; index < 3; index++) {
            createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                    "idem original " + index + " " + UUID.randomUUID(), "B", List.of());
        }
        UUID userId = registerUser();

        createSelfTest(userId, catalog.subjectId(), 10, 0.6, 0.4, "idem-key", 201);
        createSelfTest(userId, catalog.subjectId(), 5, 0.6, 0.4, "idem-key", 409);
    }

    private int countSourceTypes(final JsonNode session, final String sourceType) {
        int count = 0;
        for (JsonNode item : session.path("items")) {
            if (sourceType.equals(item.path("sourceType").asText())) {
                count++;
            }
        }
        return count;
    }
}
