package com.certpractice.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.certpractice.aigateway.AiGenerationBatch;
import com.certpractice.aigateway.AiGenerationBatchRepository;
import com.certpractice.aigateway.AiGenerationCandidate;
import com.certpractice.aigateway.AiGenerationCandidateRepository;
import com.certpractice.practice.PracticeSession;
import com.certpractice.practice.PracticeSessionRepository;
import com.certpractice.practice.SelfAssessment;
import com.certpractice.practice.SelfAssessmentRepository;
import com.certpractice.practice.SessionItem;
import com.certpractice.practice.SessionItemRepository;
import com.certpractice.practice.UserAnswer;
import com.certpractice.practice.UserAnswerRepository;
import com.certpractice.question.AiVariantSource;
import com.certpractice.question.AiVariantSourceRepository;
import com.certpractice.question.Question;
import com.certpractice.question.QuestionDtos;
import com.certpractice.question.QuestionOptionRepository;
import com.certpractice.question.QuestionRepository;
import com.certpractice.question.QuestionVersion;
import com.certpractice.question.QuestionVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Shared fixtures for P3 self-test and AI gateway tests. The fake AI server is
 * started once per test JVM and selected through a dynamic property so the real
 * {@code AiServiceClient} is exercised.
 */
public abstract class P3TestSupport extends P1TestSupport {

    protected static final FakeAiServer AI_SERVER = FakeAiServer.start();

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected AiGenerationBatchRepository aiBatches;

    @Autowired
    protected AiGenerationCandidateRepository aiCandidates;

    @Autowired
    protected AiVariantSourceRepository aiVariantSources;

    @Autowired
    protected PracticeSessionRepository practiceSessions;

    @Autowired
    protected SessionItemRepository sessionItems;

    @Autowired
    protected UserAnswerRepository userAnswers;

    @Autowired
    protected SelfAssessmentRepository selfAssessments;

    @Autowired
    protected QuestionRepository questions;

    @Autowired
    protected QuestionVersionRepository questionVersions;

    @Autowired
    protected QuestionOptionRepository questionOptions;

    @DynamicPropertySource
    static void registerAiService(final DynamicPropertyRegistry registry) {
        registry.add("app.ai.base-url", AI_SERVER::baseUrl);
        registry.add("app.ai.generation.scheduler-enabled", () -> "false");
    }

    @BeforeEach
    void resetAiFixtures() {
        AI_SERVER.reset();
        jdbcTemplate.update("DELETE FROM ai_message");
        jdbcTemplate.update("DELETE FROM ai_request");
        jdbcTemplate.update("DELETE FROM ai_conversation");
        jdbcTemplate.update("DELETE FROM ai_usage_daily");
        jdbcTemplate.update("DELETE FROM ai_budget_daily");
        jdbcTemplate.update("DELETE FROM system_config_version");
    }

    /**
     * Publishes an original question and records it as an approved AI variant
     * sourced from another version, which is the only shape the self-test pool
     * may accept.
     */
    protected QuestionRef createApprovedAiVariant(
            final UUID subjectId,
            final UUID chapterId,
            final UUID sourceVersionId,
            final String stem,
            final String correctKey) {
        QuestionRef variant = createPublishedChoice(subjectId, chapterId, stem, correctKey, List.of());
        AiGenerationBatch batch = createBatch(subjectId, chapterId, sourceVersionId);
        AiGenerationCandidate candidate = createCandidate(
                batch.getId(), sourceVersionId, variant.versionId(),
                AiGenerationCandidate.ValidationStatus.VALID,
                AiGenerationCandidate.ReviewStatus.APPROVED);
        recordVariantSource(variant.versionId(), sourceVersionId, candidate.getId());
        return variant;
    }

    /**
     * Creates an unpublished question that claims AI-variant provenance. The
     * selection pool must ignore it because only published versions qualify.
     */
    protected QuestionRef createUnpublishedAiVariant(
            final UUID subjectId,
            final UUID chapterId,
            final UUID sourceVersionId,
            final String stem) {
        QuestionRef detail = createDraftChoice(subjectId, chapterId, stem);
        AiGenerationBatch batch = createBatch(subjectId, chapterId, sourceVersionId);
        AiGenerationCandidate candidate = createCandidate(
                batch.getId(), sourceVersionId, detail.versionId(),
                AiGenerationCandidate.ValidationStatus.VALID,
                AiGenerationCandidate.ReviewStatus.PENDING);
        recordVariantSource(detail.versionId(), sourceVersionId, candidate.getId());
        return detail;
    }

    protected AiGenerationBatch createBatch(
            final UUID subjectId,
            final UUID chapterId,
            final UUID sourceVersionId) {
        AiGenerationBatch batch = new AiGenerationBatch();
        batch.setRequestedBy(ADMIN_ID);
        batch.setSubjectId(subjectId);
        batch.setChapterId(chapterId);
        batch.setSourceQuestionIds("[\"" + sourceVersionId + "\"]");
        batch.setTargetCount(1);
        batch.setModelName("mock-model");
        batch.setPromptVersion("v1");
        batch.setStatus(AiGenerationBatch.Status.COMPLETED);
        return aiBatches.save(batch);
    }

    protected AiGenerationCandidate createCandidate(
            final UUID batchId,
            final UUID sourceVersionId,
            final UUID convertedVersionId,
            final AiGenerationCandidate.ValidationStatus validationStatus,
            final AiGenerationCandidate.ReviewStatus reviewStatus) {
        AiGenerationCandidate candidate = new AiGenerationCandidate();
        candidate.setBatchId(batchId);
        candidate.setSourceQuestionVersionId(sourceVersionId);
        candidate.setCandidateJson("{\"stem\":\"seed candidate\"}");
        candidate.setContentHash(contentHash());
        candidate.setValidationStatus(validationStatus);
        candidate.setReviewStatus(reviewStatus);
        candidate.setConvertedQuestionVersionId(convertedVersionId);
        return aiCandidates.save(candidate);
    }

    protected void recordVariantSource(
            final UUID variantVersionId,
            final UUID sourceVersionId,
            final UUID candidateId) {
        AiVariantSource source = new AiVariantSource();
        source.setQuestionVersionId(variantVersionId);
        source.setSourceQuestionVersionId(sourceVersionId);
        source.setGenerationCandidateId(candidateId);
        source.setModelName("mock-model");
        source.setPromptVersion("v1");
        aiVariantSources.save(source);
    }

    protected String contentHash() {
        String value = UUID.randomUUID().toString().replace("-", "");
        return value + value;
    }

    protected CompletedItem createCompletedItem(
            final UUID userId,
            final QuestionRef reference,
            final SessionItem.Result result) {
        Question question = questions.findById(reference.questionId())
                .orElseThrow(() -> new IllegalStateException("Question missing in test fixture"));
        QuestionVersion version = questionVersions.findById(reference.versionId())
                .orElseThrow(() -> new IllegalStateException("Version missing in test fixture"));

        PracticeSession session = new PracticeSession();
        session.setUserId(userId);
        session.setMode(PracticeSession.Mode.NORMAL);
        session.setStatus(PracticeSession.Status.COMPLETED);
        session.setFilterSnapshot("{}");
        session.setRuleSnapshot("{}");
        session.setRequestedCount(1);
        session.setActualCount(1);
        session.setOriginalCount(1);
        session.setAiVariantCount(0);
        practiceSessions.save(session);

        SessionItem item = new SessionItem();
        item.setSessionId(session.getId());
        item.setOrdinal(1);
        item.setQuestionId(reference.questionId());
        item.setQuestionVersionId(reference.versionId());
        item.setSourceType(SessionItem.SourceType.ORIGINAL);
        item.setSelectionBucket(SessionItem.SelectionBucket.UNSEEN);
        item.setFinalResult(result);
        sessionItems.save(item);

        UserAnswer answer = new UserAnswer();
        answer.setSessionItemId(item.getId());
        answer.setAnswerRevision(1);
        if (question.getType() == Question.Type.SINGLE_CHOICE) {
            UUID optionId = questionOptions.findByQuestionVersionIdOrderBySortOrderAsc(version.getId())
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Choice option missing in test fixture"))
                    .getId();
            answer.setResponseJson("{\"selectedOptionId\":\"" + optionId + "\"}");
            answer.setAutoMatchResult(UserAnswer.AutoMatchResult.NOT_APPLICABLE);
        } else {
            answer.setResponseJson("{\"text\":\"seed answer\"}");
            answer.setNormalizedAnswer("seed answer");
            answer.setAutoMatchResult(UserAnswer.AutoMatchResult.MATCH);
            SelfAssessment assessment = new SelfAssessment();
            assessment.setSessionItemId(item.getId());
            assessment.setResult(result == SessionItem.Result.PASS
                    ? SelfAssessment.Result.PASS
                    : SelfAssessment.Result.FAIL);
            assessment.setAssessedBy(userId);
            selfAssessments.save(assessment);
        }
        userAnswers.save(answer);
        return new CompletedItem(session.getId(), item.getId(), reference);
    }

    protected void markSessionStatus(
            final UUID userId,
            final QuestionRef reference,
            final PracticeSession.Status sessionStatus) {
        CompletedItem item = createCompletedItem(userId, reference, SessionItem.Result.PASS);
        PracticeSession session = practiceSessions.findById(item.sessionId())
                .orElseThrow(() -> new IllegalStateException("Session missing in test fixture"));
        session.setStatus(sessionStatus);
        practiceSessions.save(session);
    }

    protected JsonNode createSelfTest(
            final UUID userId,
            final UUID subjectId,
            final int requestedCount,
            final Double originalRatio,
            final Double aiVariantRatio,
            final String idempotencyKey,
            final int expectedStatus) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("mode", "SELF_TEST");
        body.put("filters", Map.of("subjectId", subjectId.toString(), "historyDays", 90));
        body.put("requestedCount", requestedCount);
        if (originalRatio != null || aiVariantRatio != null) {
            body.put("composition", Map.of(
                    "originalRatio", originalRatio == null ? 0.6 : originalRatio,
                    "aiVariantRatio", aiVariantRatio == null ? 0.4 : aiVariantRatio));
        }
        MockHttpServletRequestBuilder request = post("/api/v1/practice-sessions")
                .with(appUser(userId, usernameFor(userId)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return readJson(result);
    }

    protected UUID createGeneralConversation(final UUID userId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/ai/conversations")
                        .with(appUser(userId, usernameFor(userId)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("scope", "GENERAL"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(result).path("conversationId").asText());
    }

    protected MvcResult sendMessage(
            final UUID userId,
            final UUID conversationId,
            final String content,
            final String idempotencyKey,
            final int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder request = post(
                "/api/v1/ai/conversations/{id}/messages", conversationId)
                .with(appUser(userId, usernameFor(userId)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("content", content)));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    protected MvcResult sendMessageRaw(
            final UUID userId,
            final UUID conversationId,
            final String content,
            final String idempotencyKey) throws Exception {
        MockHttpServletRequestBuilder request = post(
                "/api/v1/ai/conversations/{id}/messages", conversationId)
                .with(appUser(userId, usernameFor(userId)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("content", content)));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request).andReturn();
    }

    protected JsonNode getJson(final String path, final UUID userId) throws Exception {
        MvcResult result = mockMvc.perform(get(path).with(appUser(userId, usernameFor(userId))))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result);
    }

    protected JsonNode getAdminJson(final String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path).with(admin()))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result);
    }

    protected String usernameFor(final UUID userId) {
        return "user-" + userId.toString().substring(0, 8);
    }

    protected JsonNode readJson(final MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected record CompletedItem(UUID sessionId, UUID itemId, QuestionRef reference) {
    }

    private QuestionRef createDraftChoice(
            final UUID subjectId,
            final UUID chapterId,
            final String stem) {
        QuestionDtos.AdminQuestionDetail detail = authoringService.createQuestion(ADMIN_ID,
                new QuestionDtos.CreateQuestionRequest(
                        subjectId, chapterId, "SINGLE_CHOICE", stem,
                        "Draft explanation", null, "MEDIUM", "Test source",
                        List.of(
                                new QuestionDtos.OptionInput("A", "Option A", true),
                                new QuestionDtos.OptionInput("B", "Option B", false)),
                        List.of(), List.of()));
        return new QuestionRef(detail.questionId(), detail.versions().get(0).versionId());
    }
}
