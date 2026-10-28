package com.certpractice.practice;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.certpractice.question.Question;
import com.certpractice.question.QuestionDtos;
import com.certpractice.question.QuestionOption;
import com.certpractice.question.QuestionOptionRepository;
import com.certpractice.question.QuestionRepository;
import com.certpractice.question.QuestionSearchService;
import com.certpractice.question.QuestionVersion;
import com.certpractice.question.QuestionVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/**
 * Maps persistence entities into answer-safe session views and post-submission
 * result views. Only the result mapper can expose correct answers and
 * explanations.
 */
@Service
public class PracticeDtoMapper {

    private final SessionItemRepository sessionItems;
    private final UserAnswerRepository answers;
    private final SelfAssessmentRepository assessments;
    private final QuestionRepository questions;
    private final QuestionVersionRepository versions;
    private final QuestionOptionRepository options;
    private final QuestionSearchService questionSearchService;
    private final ObjectMapper objectMapper;

    public PracticeDtoMapper(
            final SessionItemRepository sessionItems,
            final UserAnswerRepository answers,
            final SelfAssessmentRepository assessments,
            final QuestionRepository questions,
            final QuestionVersionRepository versions,
            final QuestionOptionRepository options,
            final QuestionSearchService questionSearchService,
            final ObjectMapper objectMapper) {
        this.sessionItems = sessionItems;
        this.answers = answers;
        this.assessments = assessments;
        this.questions = questions;
        this.versions = versions;
        this.options = options;
        this.questionSearchService = questionSearchService;
        this.objectMapper = objectMapper;
    }

    public List<SessionItem> items(final UUID sessionId) {
        return sessionItems.findBySessionIdOrderByOrdinalAsc(sessionId);
    }

    public PracticeDtos.SessionView toSessionView(
            final PracticeSession session,
            final List<SessionItem> items) {
        int unseen = 0;
        int wrong = 0;
        int normal = 0;
        List<PracticeDtos.SessionItemView> itemViews = new ArrayList<>();
        for (SessionItem item : items) {
            switch (item.getSelectionBucket()) {
                case UNSEEN -> unseen++;
                case WRONG -> wrong++;
                case NORMAL -> normal++;
                default -> {
                }
            }
            Optional<UserAnswer> answer = answers.findBySessionItemId(item.getId());
            Optional<SelfAssessment> assessment = assessments.findBySessionItemId(item.getId());
            itemViews.add(new PracticeDtos.SessionItemView(
                    item.getId(),
                    item.getOrdinal(),
                    item.getSourceType().name(),
                    item.getSelectionBucket().name(),
                    safeQuestion(item),
                    answer.map(this::answerView).orElse(null),
                    answer.map(stored -> stored.getAutoMatchResult().name()).orElse(null),
                    assessment.map(stored -> stored.getResult().name()).orElse(null),
                    item.getFinalResult() == null ? null : item.getFinalResult().name()));
        }
        return new PracticeDtos.SessionView(
                session.getId(),
                session.getMode().name(),
                session.getStatus().name(),
                session.getRowVersion() == null ? 0 : session.getRowVersion(),
                session.getRequestedCount(),
                session.getActualCount(),
                new PracticeDtos.CompositionView(
                        session.getOriginalCount(),
                        session.getAiVariantCount(),
                        new PracticeDtos.BucketCounts(unseen, wrong, normal)),
                allowedActions(session.getStatus()),
                itemViews);
    }

    public PracticeDtos.ResultView toResultView(
            final PracticeSession session,
            final List<SessionItem> items) {
        List<PracticeDtos.ResultItemView> resultItems = new ArrayList<>();
        int objectiveAnswered = 0;
        int objectiveCorrect = 0;
        int autoMatched = 0;
        int selfPassed = 0;
        int selfFailed = 0;

        for (SessionItem item : items) {
            Question question = questions.findById(item.getQuestionId()).orElse(null);
            QuestionVersion version = versions.findById(item.getQuestionVersionId()).orElse(null);
            if (question == null || version == null) {
                continue;
            }
            Optional<UserAnswer> storedAnswer = answers.findBySessionItemId(item.getId());
            Optional<SelfAssessment> assessment = assessments.findBySessionItemId(item.getId());
            AnswerParts parts = storedAnswer.map(this::answerParts).orElse(new AnswerParts(null, null, 0));

            UUID correctOptionId = null;
            if (question.getType() == Question.Type.SINGLE_CHOICE) {
                if (storedAnswer.isPresent()) {
                    objectiveAnswered++;
                }
                if (item.getFinalResult() == SessionItem.Result.PASS) {
                    objectiveCorrect++;
                }
                correctOptionId = options.findByQuestionVersionIdAndCorrectTrue(version.getId())
                        .stream()
                        .findFirst()
                        .map(QuestionOption::getId)
                        .orElse(null);
            } else {
                if (storedAnswer.map(answer -> answer.getAutoMatchResult()
                        == UserAnswer.AutoMatchResult.MATCH).orElse(false)) {
                    autoMatched++;
                }
                if (item.getFinalResult() == SessionItem.Result.PASS) {
                    selfPassed++;
                } else if (item.getFinalResult() == SessionItem.Result.FAIL) {
                    selfFailed++;
                }
            }

            resultItems.add(new PracticeDtos.ResultItemView(
                    item.getId(),
                    item.getQuestionId(),
                    item.getQuestionVersionId(),
                    question.getType().name(),
                    version.getStem(),
                    version.getDifficulty().name(),
                    item.getFinalResult() == null ? null : item.getFinalResult().name(),
                    parts.selectedOptionId(),
                    parts.text(),
                    correctOptionId,
                    version.getFillReferenceAnswer(),
                    version.getExplanation(),
                    storedAnswer.map(answer -> answer.getAutoMatchResult().name()).orElse(null),
                    assessment.map(stored -> stored.getResult().name()).orElse(null),
                    safeOptions(version.getId())));
        }

        return new PracticeDtos.ResultView(
                session.getId(),
                session.getMode().name(),
                session.getStatus().name(),
                session.getRequestedCount(),
                session.getActualCount(),
                session.getStartedAt(),
                session.getSubmittedAt(),
                session.getCompletedAt(),
                new PracticeDtos.ObjectiveSummary(objectiveAnswered, objectiveCorrect),
                new PracticeDtos.FillSummary(autoMatched, selfPassed, selfFailed),
                resultItems);
    }

    public PracticeDtos.RecordSummary toRecordSummary(
            final PracticeSession session,
            final List<SessionItem> items) {
        int answered = 0;
        int correct = 0;
        for (SessionItem item : items) {
            Question question = questions.findById(item.getQuestionId()).orElse(null);
            if (question == null || question.getType() != Question.Type.SINGLE_CHOICE) {
                continue;
            }
            if (answers.findBySessionItemId(item.getId()).isPresent()) {
                answered++;
            }
            if (item.getFinalResult() == SessionItem.Result.PASS) {
                correct++;
            }
        }
        return new PracticeDtos.RecordSummary(
                session.getId(),
                session.getMode().name(),
                session.getStatus().name(),
                session.getRequestedCount(),
                session.getActualCount(),
                correct,
                answered,
                session.getStartedAt(),
                session.getSubmittedAt(),
                session.getCompletedAt());
    }

    public PracticeDtos.WrongQuestionView toWrongQuestionView(final WrongQuestion wrong) {
        Question question = questions.findById(wrong.getQuestionId()).orElse(null);
        if (question == null) {
            return null;
        }
        QuestionVersion version = currentVersion(question);
        if (version == null) {
            return null;
        }
        return new PracticeDtos.WrongQuestionView(
                question.getId(),
                version.getId(),
                question.getType().name(),
                version.getStem(),
                version.getDifficulty().name(),
                question.getStatus().name(),
                wrong.getLastFailedAt());
    }

    public QuestionDtos.PublicQuestionView safeQuestion(final SessionItem item) {
        Question question = questions.findById(item.getQuestionId()).orElse(null);
        QuestionVersion version = versions.findById(item.getQuestionVersionId()).orElse(null);
        if (question == null || version == null) {
            return null;
        }
        return new QuestionDtos.PublicQuestionView(
                question.getId(),
                version.getId(),
                question.getType().name(),
                version.getStem(),
                version.getDifficulty().name(),
                questionSearchService.chapterRef(question.getChapterId()),
                questionSearchService.tagRefs(version.getId()),
                safeOptions(version.getId()));
    }

    public UUID correctOptionId(final UUID versionId) {
        return options.findByQuestionVersionIdAndCorrectTrue(versionId)
                .stream()
                .findFirst()
                .map(QuestionOption::getId)
                .orElse(null);
    }

    public List<QuestionDtos.OptionSafe> safeOptions(final UUID versionId) {
        return options.findByQuestionVersionIdOrderBySortOrderAsc(versionId)
                .stream()
                .map(option -> new QuestionDtos.OptionSafe(
                        option.getId(),
                        option.getOptionKey(),
                        option.getContent()))
                .toList();
    }

    private QuestionVersion currentVersion(final Question question) {
        if (question.getPublishedVersionId() != null) {
            return versions.findById(question.getPublishedVersionId()).orElse(null);
        }
        return versions.findFirstByQuestionIdOrderByVersionNoDesc(question.getId()).orElse(null);
    }

    private PracticeDtos.AnswerView answerView(final UserAnswer answer) {
        AnswerParts parts = answerParts(answer);
        return new PracticeDtos.AnswerView(parts.selectedOptionId(), parts.text(), parts.revision());
    }

    private AnswerParts answerParts(final UserAnswer answer) {
        if (answer == null || answer.getResponseJson() == null) {
            return new AnswerParts(null, null, 0);
        }
        try {
            JsonNode node = objectMapper.readTree(answer.getResponseJson());
            UUID selectedOptionId = node.hasNonNull("selectedOptionId")
                    ? UUID.fromString(node.get("selectedOptionId").asText())
                    : null;
            String text = node.hasNonNull("text") ? node.get("text").asText() : null;
            return new AnswerParts(selectedOptionId, text, answer.getAnswerRevision());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return new AnswerParts(null, null, answer.getAnswerRevision());
        }
    }

    private List<String> allowedActions(final PracticeSession.Status status) {
        return switch (status) {
            case IN_PROGRESS -> List.of("SAVE_ANSWER", "SUBMIT", "ABANDON");
            case SUBMITTED -> List.of("VIEW_RESULT");
            case PENDING_SELF_ASSESSMENT -> List.of("SELF_ASSESS", "VIEW_RESULT");
            case COMPLETED -> List.of("VIEW_RESULT");
            case ABANDONED -> List.of();
        };
    }

    private record AnswerParts(UUID selectedOptionId, String text, int revision) {
    }
}
