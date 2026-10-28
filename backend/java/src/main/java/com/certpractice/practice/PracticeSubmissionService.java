package com.certpractice.practice;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.certpractice.identity.IdempotencyRecord;
import com.certpractice.question.Question;
import com.certpractice.question.QuestionAnswerAlias;
import com.certpractice.question.QuestionAnswerAliasRepository;
import com.certpractice.question.QuestionOption;
import com.certpractice.question.QuestionOptionRepository;
import com.certpractice.question.QuestionRepository;
import com.certpractice.question.QuestionVersion;
import com.certpractice.question.QuestionVersionRepository;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.idempotency.IdempotencyService;
import com.certpractice.shared.text.TextNormalizer;
import com.certpractice.shared.web.Guard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Locked, idempotent submission. Single-choice items are graded immediately;
 * single-blank items record an automatic match suggestion and wait for the
 * owner's self-assessment.
 */
@Service
public class PracticeSubmissionService {

    private static final String SUBMIT_OPERATION = "SUBMIT_PRACTICE_SESSION";

    private final PracticeSessionRepository sessions;
    private final SessionItemRepository sessionItems;
    private final UserAnswerRepository answers;
    private final QuestionRepository questions;
    private final QuestionVersionRepository versions;
    private final QuestionOptionRepository options;
    private final QuestionAnswerAliasRepository aliases;
    private final WrongQuestionService wrongQuestions;
    private final PracticeDtoMapper mapper;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;

    public PracticeSubmissionService(
            final PracticeSessionRepository sessions,
            final SessionItemRepository sessionItems,
            final UserAnswerRepository answers,
            final QuestionRepository questions,
            final QuestionVersionRepository versions,
            final QuestionOptionRepository options,
            final QuestionAnswerAliasRepository aliases,
            final WrongQuestionService wrongQuestions,
            final PracticeDtoMapper mapper,
            final IdempotencyService idempotency,
            final ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.sessionItems = sessionItems;
        this.answers = answers;
        this.questions = questions;
        this.versions = versions;
        this.options = options;
        this.aliases = aliases;
        this.wrongQuestions = wrongQuestions;
        this.mapper = mapper;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PracticeDtos.ResultView submit(
            final UUID userId,
            final UUID sessionId,
            final String idempotencyKey) {
        Optional<IdempotencyRecord> existing = idempotency.find(
                userId, SUBMIT_OPERATION, idempotencyKey, sessionId.toString());
        if (existing.isPresent()) {
            return idempotency.replay(existing.get(), PracticeDtos.ResultView.class)
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
                            "Stored submission response is unreadable"));
        }

        PracticeSession session = sessions.findForUpdate(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Practice session not found"));

        if (session.getStatus() == PracticeSession.Status.SUBMITTED
                || session.getStatus() == PracticeSession.Status.PENDING_SELF_ASSESSMENT
                || session.getStatus() == PracticeSession.Status.COMPLETED) {
            List<SessionItem> frozenItems = mapper.items(sessionId);
            PracticeDtos.ResultView frozen = mapper.toResultView(session, frozenItems);
            idempotency.store(userId, SUBMIT_OPERATION, idempotencyKey, sessionId.toString(), 200, frozen);
            return frozen;
        }
        Guard.require(session.getStatus() == PracticeSession.Status.IN_PROGRESS,
                ErrorCode.STATE_CONFLICT, "Session cannot be submitted in its current state");

        List<SessionItem> items = mapper.items(sessionId);
        for (SessionItem item : items) {
            if (answers.findBySessionItemId(item.getId()).isEmpty()) {
                throw new ApiException(ErrorCode.ANSWERS_INCOMPLETE,
                        "All session items must be answered before submission");
            }
        }

        boolean pendingSelfAssessment = false;
        for (SessionItem item : items) {
            Question question = questions.findById(item.getQuestionId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Question not found"));
            QuestionVersion version = versions.findById(item.getQuestionVersionId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Question version not found"));
            UserAnswer answer = answers.findBySessionItemId(item.getId())
                    .orElseThrow(() -> new ApiException(ErrorCode.ANSWERS_INCOMPLETE,
                            "All session items must be answered before submission"));

            if (question.getType() == Question.Type.SINGLE_CHOICE) {
                UUID selectedOptionId = selectedOptionId(answer.getResponseJson());
                QuestionOption option = selectedOptionId == null ? null
                        : options.findByIdAndQuestionVersionId(selectedOptionId, version.getId()).orElse(null);
                boolean correct = option != null && option.isCorrect();
                item.setFinalResult(correct ? SessionItem.Result.PASS : SessionItem.Result.FAIL);
                if (!correct) {
                    wrongQuestions.recordFailure(userId, item.getQuestionId(), item.getId());
                }
            } else {
                boolean matched = matchesReference(answer.getNormalizedAnswer(), version);
                answer.setAutoMatchResult(matched
                        ? UserAnswer.AutoMatchResult.MATCH
                        : UserAnswer.AutoMatchResult.MISMATCH);
                answers.save(answer);
                item.setFinalResult(null);
                pendingSelfAssessment = true;
            }
            sessionItems.save(item);
        }

        session.setSubmittedAt(Instant.now());
        if (pendingSelfAssessment) {
            session.setStatus(PracticeSession.Status.PENDING_SELF_ASSESSMENT);
        } else {
            session.setStatus(PracticeSession.Status.COMPLETED);
            session.setCompletedAt(Instant.now());
        }
        sessions.save(session);

        PracticeDtos.ResultView result = mapper.toResultView(session, mapper.items(sessionId));
        idempotency.store(userId, SUBMIT_OPERATION, idempotencyKey, sessionId.toString(), 200, result);
        return result;
    }

    private boolean matchesReference(final String normalizedAnswer, final QuestionVersion version) {
        if (normalizedAnswer == null || version.getFillReferenceAnswer() == null) {
            return false;
        }
        String reference = TextNormalizer.normalizeFillAnswer(version.getFillReferenceAnswer());
        if (normalizedAnswer.equals(reference)) {
            return true;
        }
        for (QuestionAnswerAlias alias : aliases.findByQuestionVersionId(version.getId())) {
            if (normalizedAnswer.equals(alias.getNormalizedValue())) {
                return true;
            }
        }
        return false;
    }

    private UUID selectedOptionId(final String responseJson) {
        try {
            JsonNode node = objectMapper.readTree(responseJson);
            return node.hasNonNull("selectedOptionId")
                    ? UUID.fromString(node.get("selectedOptionId").asText())
                    : null;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return null;
        }
    }
}
