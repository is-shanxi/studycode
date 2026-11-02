package com.certpractice.practice;

import java.util.Optional;
import java.util.UUID;

import com.certpractice.question.Question;
import com.certpractice.question.QuestionOption;
import com.certpractice.question.QuestionOptionRepository;
import com.certpractice.question.QuestionRepository;
import com.certpractice.question.QuestionVersion;
import com.certpractice.question.QuestionVersionRepository;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.web.Guard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public query port used by the AI gateway. It only returns context for an item
 * owned by the caller whose session has completed; the gateway decides whether
 * the official explanation may leave the server.
 */
@Service
public class PracticeAiContextService {

    private final PracticeSessionRepository sessions;
    private final SessionItemRepository sessionItems;
    private final UserAnswerRepository answers;
    private final SelfAssessmentRepository assessments;
    private final QuestionRepository questions;
    private final QuestionVersionRepository versions;
    private final QuestionOptionRepository options;
    private final ObjectMapper objectMapper;

    public PracticeAiContextService(
            final PracticeSessionRepository sessions,
            final SessionItemRepository sessionItems,
            final UserAnswerRepository answers,
            final SelfAssessmentRepository assessments,
            final QuestionRepository questions,
            final QuestionVersionRepository versions,
            final QuestionOptionRepository options,
            final ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.sessionItems = sessionItems;
        this.answers = answers;
        this.assessments = assessments;
        this.questions = questions;
        this.versions = versions;
        this.options = options;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AiQuestionContext requireCompletedItem(final UUID userId, final UUID sessionItemId) {
        SessionItem item = sessionItems.findById(sessionItemId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Session item not found"));
        PracticeSession session = sessions.findByIdAndUserId(item.getSessionId(), userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Session item not found"));
        Guard.require(session.getStatus() == PracticeSession.Status.COMPLETED,
                ErrorCode.STATE_CONFLICT,
                "Question AI is available only after the session is completed");
        Guard.require(item.getFinalResult() != null, ErrorCode.STATE_CONFLICT,
                "Question AI is available only after the item has a final result");

        Question question = questions.findById(item.getQuestionId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Question not found"));
        QuestionVersion version = versions.findById(item.getQuestionVersionId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Question version not found"));
        UserAnswer answer = answers.findBySessionItemId(sessionItemId).orElse(null);
        SelfAssessment assessment = assessments.findBySessionItemId(sessionItemId).orElse(null);

        return new AiQuestionContext(
                question.getId(),
                version.getId(),
                item.getId(),
                version.getStem(),
                question.getType().name(),
                answerText(question, version, answer),
                assessment == null ? item.getFinalResult().name() : assessment.getResult().name(),
                version.getExplanation(),
                version.getFillReferenceAnswer());
    }

    private String answerText(
            final Question question,
            final QuestionVersion version,
            final UserAnswer answer) {
        if (answer == null || answer.getResponseJson() == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(answer.getResponseJson());
            if (question.getType() == Question.Type.SINGLE_BLANK) {
                return node.hasNonNull("text") ? node.get("text").asText() : null;
            }
            if (!node.hasNonNull("selectedOptionId")) {
                return null;
            }
            UUID optionId = UUID.fromString(node.get("selectedOptionId").asText());
            Optional<QuestionOption> option =
                    options.findByIdAndQuestionVersionId(optionId, version.getId());
            return option.map(value -> value.getOptionKey() + ": " + value.getContent())
                    .orElse(optionId.toString());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return null;
        }
    }

    public record AiQuestionContext(
            UUID questionId,
            UUID questionVersionId,
            UUID sessionItemId,
            String stem,
            String questionType,
            String userAnswer,
            String selfAssessment,
            String explanation,
            String referenceAnswer) {
    }
}
