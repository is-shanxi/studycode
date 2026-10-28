package com.certpractice.practice;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.certpractice.identity.IdempotencyRecord;
import com.certpractice.question.Question;
import com.certpractice.question.QuestionRepository;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.idempotency.IdempotencyService;
import com.certpractice.shared.web.Guard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-item fill self-assessment. The automatic match is only a suggestion; the
 * owner's confirmation produces the final result and completes the session once
 * every item is confirmed.
 */
@Service
public class FillAssessmentService {

    private static final String ASSESS_OPERATION = "SELF_ASSESS_ITEM";

    private final PracticeSessionRepository sessions;
    private final SessionItemRepository sessionItems;
    private final UserAnswerRepository answers;
    private final SelfAssessmentRepository assessments;
    private final QuestionRepository questions;
    private final WrongQuestionService wrongQuestions;
    private final PracticeDtoMapper mapper;
    private final IdempotencyService idempotency;

    public FillAssessmentService(
            final PracticeSessionRepository sessions,
            final SessionItemRepository sessionItems,
            final UserAnswerRepository answers,
            final SelfAssessmentRepository assessments,
            final QuestionRepository questions,
            final WrongQuestionService wrongQuestions,
            final PracticeDtoMapper mapper,
            final IdempotencyService idempotency) {
        this.sessions = sessions;
        this.sessionItems = sessionItems;
        this.answers = answers;
        this.assessments = assessments;
        this.questions = questions;
        this.wrongQuestions = wrongQuestions;
        this.mapper = mapper;
        this.idempotency = idempotency;
    }

    @Transactional
    public PracticeDtos.SelfAssessmentResponse assess(
            final UUID userId,
            final UUID sessionId,
            final UUID itemId,
            final PracticeDtos.SelfAssessmentRequest request,
            final String idempotencyKey) {
        Map<String, String> payload = Map.of(
                "sessionId", sessionId.toString(),
                "itemId", itemId.toString(),
                "result", request.result());
        Optional<IdempotencyRecord> existingRecord = idempotency.find(
                userId, ASSESS_OPERATION, idempotencyKey, payload);
        if (existingRecord.isPresent()) {
            return idempotency.replay(existingRecord.get(), PracticeDtos.SelfAssessmentResponse.class)
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
                            "Stored self-assessment response is unreadable"));
        }

        PracticeSession session = sessions.findForUpdate(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Practice session not found"));
        SessionItem item = sessionItems.findByIdAndSessionId(itemId, sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Session item not found"));
        Question question = questions.findById(item.getQuestionId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Question not found"));
        Guard.require(question.getType() == Question.Type.SINGLE_BLANK, ErrorCode.VALIDATION_FAILED,
                "Only single-blank items require self-assessment");
        if (answers.findBySessionItemId(itemId).isEmpty()) {
            throw new ApiException(ErrorCode.ANSWERS_INCOMPLETE, "Session item has not been answered");
        }

        SelfAssessment.Result result = parseResult(request.result());
        Optional<SelfAssessment> existing = assessments.findBySessionItemId(itemId);
        if (existing.isPresent()) {
            Guard.require(existing.get().getResult() == result, ErrorCode.STATE_CONFLICT,
                    "Self-assessment has already been confirmed with a different result");
            PracticeDtos.SelfAssessmentResponse response = new PracticeDtos.SelfAssessmentResponse(
                    itemId, result.name(), session.getStatus().name());
            idempotency.store(userId, ASSESS_OPERATION, idempotencyKey, payload, 200, response);
            return response;
        }

        Guard.require(session.getStatus() == PracticeSession.Status.PENDING_SELF_ASSESSMENT,
                ErrorCode.STATE_CONFLICT, "Session is not waiting for self-assessment");

        SelfAssessment assessment = new SelfAssessment();
        assessment.setSessionItemId(itemId);
        assessment.setResult(result);
        assessment.setAssessedBy(userId);
        assessments.save(assessment);

        item.setFinalResult(result == SelfAssessment.Result.PASS
                ? SessionItem.Result.PASS
                : SessionItem.Result.FAIL);
        sessionItems.save(item);

        if (result == SelfAssessment.Result.FAIL) {
            wrongQuestions.recordFailure(userId, item.getQuestionId(), itemId);
        }

        List<SessionItem> items = mapper.items(sessionId);
        boolean allConfirmed = items.stream().allMatch(candidate -> candidate.getFinalResult() != null);
        if (allConfirmed) {
            session.setStatus(PracticeSession.Status.COMPLETED);
            session.setCompletedAt(Instant.now());
            sessions.save(session);
        }

        PracticeDtos.SelfAssessmentResponse response = new PracticeDtos.SelfAssessmentResponse(
                itemId,
                item.getFinalResult().name(),
                session.getStatus().name());
        idempotency.store(userId, ASSESS_OPERATION, idempotencyKey, payload, 200, response);
        return response;
    }

    private SelfAssessment.Result parseResult(final String value) {
        try {
            return SelfAssessment.Result.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "result must be PASS or FAIL", exception);
        }
    }
}
