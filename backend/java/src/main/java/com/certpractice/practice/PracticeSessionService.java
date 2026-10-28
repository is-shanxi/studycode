package com.certpractice.practice;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.certpractice.identity.IdempotencyRecord;
import com.certpractice.question.Question;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Session creation, safe loading and answer saving. Answers may change until
 * submission, but never after.
 */
@Service
public class PracticeSessionService {

    private static final int DEFAULT_REQUESTED_COUNT = 10;
    private static final int MAX_REQUESTED_COUNT = 100;
    private static final String CREATE_OPERATION = "CREATE_PRACTICE_SESSION";

    private final PracticeSessionRepository sessions;
    private final SessionItemRepository sessionItems;
    private final UserAnswerRepository answers;
    private final QuestionRepository questions;
    private final QuestionVersionRepository versions;
    private final QuestionOptionRepository options;
    private final QuestionSelectionService selectionService;
    private final PracticeDtoMapper mapper;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;

    public PracticeSessionService(
            final PracticeSessionRepository sessions,
            final SessionItemRepository sessionItems,
            final UserAnswerRepository answers,
            final QuestionRepository questions,
            final QuestionVersionRepository versions,
            final QuestionOptionRepository options,
            final QuestionSelectionService selectionService,
            final PracticeDtoMapper mapper,
            final IdempotencyService idempotency,
            final ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.sessionItems = sessionItems;
        this.answers = answers;
        this.questions = questions;
        this.versions = versions;
        this.options = options;
        this.selectionService = selectionService;
        this.mapper = mapper;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PracticeDtos.SessionView create(
            final UUID userId,
            final PracticeDtos.CreateSessionRequest request,
            final String idempotencyKey) {
        Optional<IdempotencyRecord> existing = idempotency.find(
                userId, CREATE_OPERATION, idempotencyKey, request);
        if (existing.isPresent()) {
            return idempotency.replay(existing.get(), PracticeDtos.SessionView.class)
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
                            "Stored session response is unreadable"));
        }

        PracticeSession.Mode mode = parseMode(request.mode());
        int requestedCount = request.requestedCount() == null
                ? DEFAULT_REQUESTED_COUNT
                : request.requestedCount();
        Guard.require(requestedCount >= 1 && requestedCount <= MAX_REQUESTED_COUNT,
                ErrorCode.VALIDATION_FAILED,
                "requestedCount must be between 1 and " + MAX_REQUESTED_COUNT);

        QuestionSelectionService.SelectionResult selection = selectionService.select(
                userId, request.filters(), requestedCount, mode);
        int originalCount = 0;
        int aiVariantCount = 0;
        for (QuestionSelectionService.SelectedQuestion item : selection.items()) {
            if (SessionItem.SourceType.AI_VARIANT.name().equals(item.sourceType())) {
                aiVariantCount++;
            } else {
                originalCount++;
            }
        }

        PracticeSession session = new PracticeSession();
        session.setUserId(userId);
        session.setMode(mode);
        session.setStatus(PracticeSession.Status.IN_PROGRESS);
        session.setFilterSnapshot(toJson(Map.of(
                "filters", request.filters() == null ? Map.of() : request.filters(),
                "composition", request.composition() == null ? Map.of() : request.composition())));
        session.setRuleSnapshot(toJson(selection.ruleSnapshot()));
        session.setRequestedCount(requestedCount);
        session.setActualCount(selection.items().size());
        session.setOriginalCount(originalCount);
        session.setAiVariantCount(aiVariantCount);
        session.setIdempotencyKey(idempotencyKey);
        sessions.save(session);

        for (QuestionSelectionService.SelectedQuestion selected : selection.items()) {
            SessionItem item = new SessionItem();
            item.setSessionId(session.getId());
            item.setOrdinal(selected.ordinal());
            item.setQuestionId(selected.questionId());
            item.setQuestionVersionId(selected.versionId());
            item.setSourceType(SessionItem.SourceType.valueOf(selected.sourceType()));
            item.setSelectionBucket(SessionItem.SelectionBucket.valueOf(selected.selectionBucket()));
            sessionItems.save(item);
        }

        List<SessionItem> items = mapper.items(session.getId());
        PracticeDtos.SessionView view = mapper.toSessionView(session, items);
        idempotency.store(userId, CREATE_OPERATION, idempotencyKey, request, 201, view);
        return view;
    }

    @Transactional(readOnly = true)
    public PracticeDtos.SessionView getSession(final UUID userId, final UUID sessionId) {
        PracticeSession session = requireSession(userId, sessionId);
        return mapper.toSessionView(session, mapper.items(sessionId));
    }

    @Transactional
    public PracticeDtos.SaveAnswerResponse saveAnswer(
            final UUID userId,
            final UUID sessionId,
            final UUID itemId,
            final PracticeDtos.SaveAnswerRequest request) {
        PracticeSession session = requireSession(userId, sessionId);
        Guard.require(session.getStatus() == PracticeSession.Status.IN_PROGRESS,
                ErrorCode.STATE_CONFLICT, "Answers can only be changed while a session is in progress");

        SessionItem item = sessionItems.findByIdAndSessionId(itemId, sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Session item not found"));
        Question question = questions.findById(item.getQuestionId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Question not found"));
        QuestionVersion version = versions.findById(item.getQuestionVersionId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Question version not found"));

        Optional<UserAnswer> existing = answers.findBySessionItemId(itemId);
        int expectedRevision = request.answerRevision() == null ? 0 : request.answerRevision();
        int nextRevision;
        if (existing.isEmpty()) {
            Guard.require(expectedRevision == 0 || expectedRevision == 1, ErrorCode.STATE_CONFLICT,
                    "Answer revision does not match");
            nextRevision = 1;
        } else {
            Guard.require(expectedRevision == existing.get().getAnswerRevision(),
                    ErrorCode.STATE_CONFLICT, "Answer revision does not match");
            nextRevision = existing.get().getAnswerRevision() + 1;
        }

        String responseJson;
        String normalizedAnswer = null;
        if (question.getType() == Question.Type.SINGLE_CHOICE) {
            UUID selectedOptionId = request.answer().selectedOptionId();
            Guard.require(selectedOptionId != null, ErrorCode.VALIDATION_FAILED,
                    "selectedOptionId is required for single-choice questions");
            options.findByIdAndQuestionVersionId(selectedOptionId, version.getId())
                    .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                            "Selected option does not belong to this question version"));
            responseJson = toJson(Map.of("selectedOptionId", selectedOptionId.toString()));
        } else {
            String text = request.answer().text();
            Guard.require(text != null && !text.isBlank(), ErrorCode.VALIDATION_FAILED,
                    "text is required for single-blank questions");
            Guard.require(text.length() <= 2000, ErrorCode.VALIDATION_FAILED,
                    "Answer text is too long");
            String trimmed = text.trim();
            normalizedAnswer = TextNormalizer.normalizeFillAnswer(trimmed);
            responseJson = toJson(Map.of("text", trimmed));
        }

        UserAnswer answer = existing.orElseGet(UserAnswer::new);
        answer.setSessionItemId(itemId);
        answer.setResponseJson(responseJson);
        answer.setAnswerRevision(nextRevision);
        answer.setAutoMatchResult(UserAnswer.AutoMatchResult.NOT_APPLICABLE);
        answer.setNormalizedAnswer(normalizedAnswer);
        answers.save(answer);

        return new PracticeDtos.SaveAnswerResponse(itemId, nextRevision);
    }

    @Transactional
    public void abandon(final UUID userId, final UUID sessionId) {
        PracticeSession session = requireSession(userId, sessionId);
        if (session.getStatus() == PracticeSession.Status.ABANDONED) {
            return;
        }
        Guard.require(session.getStatus() == PracticeSession.Status.IN_PROGRESS,
                ErrorCode.STATE_CONFLICT, "Only in-progress sessions can be abandoned");
        session.setStatus(PracticeSession.Status.ABANDONED);
        sessions.save(session);
    }

    @Transactional(readOnly = true)
    public PracticeSession requireSession(final UUID userId, final UUID sessionId) {
        return sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Practice session not found"));
    }

    private PracticeSession.Mode parseMode(final String value) {
        try {
            return PracticeSession.Mode.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "mode must be NORMAL or SELF_TEST", exception);
        }
    }

    private String toJson(final Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Unable to store session snapshot", exception);
        }
    }
}
