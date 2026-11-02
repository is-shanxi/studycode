package com.certpractice.practice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.certpractice.catalog.Chapter;
import com.certpractice.catalog.ChapterRepository;
import com.certpractice.catalog.Tag;
import com.certpractice.catalog.TagRepository;
import com.certpractice.question.Question;
import com.certpractice.question.QuestionDtos;
import com.certpractice.question.QuestionOption;
import com.certpractice.question.QuestionOptionRepository;
import com.certpractice.question.QuestionRepository;
import com.certpractice.question.QuestionVersion;
import com.certpractice.question.QuestionVersionRepository;
import com.certpractice.question.QuestionVersionTag;
import com.certpractice.question.QuestionVersionTagRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/**
 * Maps persistence entities into answer-safe session views and post-submission
 * result views. Only the result mapper can expose correct answers and
 * explanations. Item-level lookups are batched so a full session or result does
 * not issue one query per question.
 */
@Service
public class PracticeDtoMapper {

    private final SessionItemRepository sessionItems;
    private final UserAnswerRepository answers;
    private final SelfAssessmentRepository assessments;
    private final QuestionRepository questions;
    private final QuestionVersionRepository versions;
    private final QuestionOptionRepository options;
    private final QuestionVersionTagRepository versionTags;
    private final TagRepository tags;
    private final ChapterRepository chapters;
    private final ObjectMapper objectMapper;

    public PracticeDtoMapper(
            final SessionItemRepository sessionItems,
            final UserAnswerRepository answers,
            final SelfAssessmentRepository assessments,
            final QuestionRepository questions,
            final QuestionVersionRepository versions,
            final QuestionOptionRepository options,
            final QuestionVersionTagRepository versionTags,
            final TagRepository tags,
            final ChapterRepository chapters,
            final ObjectMapper objectMapper) {
        this.sessionItems = sessionItems;
        this.answers = answers;
        this.assessments = assessments;
        this.questions = questions;
        this.versions = versions;
        this.options = options;
        this.versionTags = versionTags;
        this.tags = tags;
        this.chapters = chapters;
        this.objectMapper = objectMapper;
    }

    public List<SessionItem> items(final UUID sessionId) {
        return sessionItems.findBySessionIdOrderByOrdinalAsc(sessionId);
    }

    public PracticeDtos.SessionView toSessionView(
            final PracticeSession session,
            final List<SessionItem> items) {
        LookupContext context = loadContext(items);
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
            Optional<UserAnswer> answer = Optional.ofNullable(
                    context.answersByItemId().get(item.getId()));
            Optional<SelfAssessment> assessment = Optional.ofNullable(
                    context.assessmentsByItemId().get(item.getId()));
            itemViews.add(new PracticeDtos.SessionItemView(
                    item.getId(),
                    item.getOrdinal(),
                    item.getSourceType().name(),
                    item.getSelectionBucket().name(),
                    safeQuestion(item, context),
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
        LookupContext context = loadContext(items);
        List<PracticeDtos.ResultItemView> resultItems = new ArrayList<>();
        int objectiveAnswered = 0;
        int objectiveCorrect = 0;
        int autoMatched = 0;
        int selfPassed = 0;
        int selfFailed = 0;

        for (SessionItem item : items) {
            Question question = context.questionsById().get(item.getQuestionId());
            QuestionVersion version = context.versionsById().get(item.getQuestionVersionId());
            if (question == null || version == null) {
                continue;
            }
            Optional<UserAnswer> storedAnswer = Optional.ofNullable(
                    context.answersByItemId().get(item.getId()));
            Optional<SelfAssessment> assessment = Optional.ofNullable(
                    context.assessmentsByItemId().get(item.getId()));
            AnswerParts parts = storedAnswer.map(this::answerParts).orElse(new AnswerParts(null, null, 0));
            List<QuestionOption> versionOptions = context.optionsByVersionId()
                    .getOrDefault(version.getId(), List.of());

            UUID correctOptionId = null;
            if (question.getType() == Question.Type.SINGLE_CHOICE) {
                if (storedAnswer.isPresent()) {
                    objectiveAnswered++;
                }
                if (item.getFinalResult() == SessionItem.Result.PASS) {
                    objectiveCorrect++;
                }
                correctOptionId = versionOptions.stream()
                        .filter(QuestionOption::isCorrect)
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
                    safeOptions(versionOptions)));
        }

        return new PracticeDtos.ResultView(
                session.getId(),
                session.getMode().name(),
                session.getStatus().name(),
                session.getRequestedCount(),
                session.getActualCount(),
                new PracticeDtos.CompositionView(
                        session.getOriginalCount(),
                        session.getAiVariantCount(),
                        bucketCounts(items)),
                allowedActions(session.getStatus()),
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
        List<UUID> itemIds = items.stream().map(SessionItem::getId).toList();
        List<UUID> questionIds = items.stream().map(SessionItem::getQuestionId).distinct().toList();
        Map<UUID, Question> questionsById = questionIds.isEmpty()
                ? Map.of()
                : questions.findAllById(questionIds).stream()
                        .collect(Collectors.toMap(Question::getId, Function.identity()));
        Map<UUID, UserAnswer> answersByItemId = itemIds.isEmpty()
                ? Map.of()
                : answers.findBySessionItemIdIn(itemIds).stream()
                        .collect(Collectors.toMap(UserAnswer::getSessionItemId, Function.identity()));

        int answered = 0;
        int correct = 0;
        for (SessionItem item : items) {
            Question question = questionsById.get(item.getQuestionId());
            if (question == null || question.getType() != Question.Type.SINGLE_CHOICE) {
                continue;
            }
            if (answersByItemId.containsKey(item.getId())) {
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
        return safeQuestion(item, loadContext(List.of(item)));
    }

    public UUID correctOptionId(final UUID versionId) {
        return options.findByQuestionVersionIdAndCorrectTrue(versionId)
                .stream()
                .findFirst()
                .map(QuestionOption::getId)
                .orElse(null);
    }

    public List<QuestionDtos.OptionSafe> safeOptions(final UUID versionId) {
        return safeOptions(options.findByQuestionVersionIdOrderBySortOrderAsc(versionId));
    }

    private QuestionDtos.PublicQuestionView safeQuestion(
            final SessionItem item,
            final LookupContext context) {
        Question question = context.questionsById().get(item.getQuestionId());
        QuestionVersion version = context.versionsById().get(item.getQuestionVersionId());
        if (question == null || version == null) {
            return null;
        }
        Chapter chapter = question.getChapterId() == null
                ? null
                : context.chaptersById().get(question.getChapterId());
        QuestionDtos.ChapterRef chapterRef = chapter == null
                ? null
                : new QuestionDtos.ChapterRef(chapter.getId(), chapter.getName());
        return new QuestionDtos.PublicQuestionView(
                question.getId(),
                version.getId(),
                question.getType().name(),
                version.getStem(),
                version.getDifficulty().name(),
                chapterRef,
                context.tagsByVersionId().getOrDefault(version.getId(), List.of()),
                safeOptions(context.optionsByVersionId().getOrDefault(version.getId(), List.of())));
    }

    private LookupContext loadContext(final List<SessionItem> items) {
        if (items.isEmpty()) {
            return LookupContext.empty();
        }
        List<UUID> itemIds = items.stream().map(SessionItem::getId).toList();
        List<UUID> questionIds = items.stream()
                .map(SessionItem::getQuestionId)
                .distinct()
                .toList();
        List<UUID> versionIds = items.stream()
                .map(SessionItem::getQuestionVersionId)
                .distinct()
                .toList();

        Map<UUID, UserAnswer> answersByItemId = answers.findBySessionItemIdIn(itemIds).stream()
                .collect(Collectors.toMap(UserAnswer::getSessionItemId, Function.identity()));
        Map<UUID, SelfAssessment> assessmentsByItemId =
                assessments.findBySessionItemIdIn(itemIds).stream()
                        .collect(Collectors.toMap(
                                SelfAssessment::getSessionItemId, Function.identity()));
        Map<UUID, Question> questionsById = questions.findAllById(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        Map<UUID, QuestionVersion> versionsById = versions.findAllById(versionIds).stream()
                .collect(Collectors.toMap(QuestionVersion::getId, Function.identity()));
        Map<UUID, List<QuestionOption>> optionsByVersionId =
                options.findByQuestionVersionIdInOrderBySortOrderAsc(versionIds).stream()
                        .collect(Collectors.groupingBy(QuestionOption::getQuestionVersionId));

        List<UUID> chapterIds = questionsById.values().stream()
                .map(Question::getChapterId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, Chapter> chaptersById = chapterIds.isEmpty()
                ? Map.of()
                : chapters.findAllById(chapterIds).stream()
                        .collect(Collectors.toMap(Chapter::getId, Function.identity()));

        return new LookupContext(
                answersByItemId,
                assessmentsByItemId,
                questionsById,
                versionsById,
                optionsByVersionId,
                tagRefsByVersionId(versionIds),
                chaptersById);
    }

    private Map<UUID, List<QuestionDtos.TagRef>> tagRefsByVersionId(
            final List<UUID> versionIds) {
        List<QuestionVersionTag> links = versionTags.findByIdQuestionVersionIdIn(versionIds);
        if (links.isEmpty()) {
            return Map.of();
        }
        List<UUID> tagIds = links.stream()
                .map(link -> link.getId().getTagId())
                .distinct()
                .toList();
        Map<UUID, Tag> tagsById = tags.findAllById(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, Function.identity()));

        Map<UUID, List<QuestionDtos.TagRef>> refs = new HashMap<>();
        for (QuestionVersionTag link : links) {
            Tag tag = tagsById.get(link.getId().getTagId());
            if (tag == null) {
                continue;
            }
            refs.computeIfAbsent(link.getId().getQuestionVersionId(), key -> new ArrayList<>())
                    .add(new QuestionDtos.TagRef(tag.getId(), tag.getName()));
        }
        refs.values().forEach(list -> list.sort(
                Comparator.comparing(QuestionDtos.TagRef::name)));
        return refs;
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
            case COMPLETED -> List.of("VIEW_RESULT", "AI_TUTOR");
            case ABANDONED -> List.of();
        };
    }

    private PracticeDtos.BucketCounts bucketCounts(final List<SessionItem> items) {
        int unseen = 0;
        int wrong = 0;
        int normal = 0;
        for (SessionItem item : items) {
            switch (item.getSelectionBucket()) {
                case UNSEEN -> unseen++;
                case WRONG -> wrong++;
                case NORMAL -> normal++;
                default -> {
                }
            }
        }
        return new PracticeDtos.BucketCounts(unseen, wrong, normal);
    }

    private List<QuestionDtos.OptionSafe> safeOptions(final List<QuestionOption> versionOptions) {
        return versionOptions.stream()
                .map(option -> new QuestionDtos.OptionSafe(
                        option.getId(),
                        option.getOptionKey(),
                        option.getContent()))
                .toList();
    }

    private record AnswerParts(UUID selectedOptionId, String text, int revision) {
    }

    private record LookupContext(
            Map<UUID, UserAnswer> answersByItemId,
            Map<UUID, SelfAssessment> assessmentsByItemId,
            Map<UUID, Question> questionsById,
            Map<UUID, QuestionVersion> versionsById,
            Map<UUID, List<QuestionOption>> optionsByVersionId,
            Map<UUID, List<QuestionDtos.TagRef>> tagsByVersionId,
            Map<UUID, Chapter> chaptersById) {

        private static LookupContext empty() {
            return new LookupContext(
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }
}
