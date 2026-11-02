package com.certpractice.question;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.certpractice.catalog.Chapter;
import com.certpractice.catalog.ChapterRepository;
import com.certpractice.catalog.Subject;
import com.certpractice.catalog.SubjectRepository;
import com.certpractice.catalog.Tag;
import com.certpractice.catalog.TagRepository;
import com.certpractice.identity.AdminAuditLog;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.text.TextNormalizer;
import com.certpractice.shared.web.Guard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Converts an approved AI candidate into an immutable, published question
 * version. This is the only path by which generated content enters the formal
 * question bank, and it records the source version and generation provenance.
 */
@Service
public class AiVariantPublisher {

    private final QuestionRepository questions;
    private final QuestionVersionRepository versions;
    private final QuestionOptionRepository options;
    private final QuestionVersionTagRepository versionTags;
    private final TagRepository tags;
    private final SubjectRepository subjects;
    private final ChapterRepository chapters;
    private final AiVariantSourceRepository variantSources;
    private final QuestionAuditService audit;

    public AiVariantPublisher(
            final QuestionRepository questions,
            final QuestionVersionRepository versions,
            final QuestionOptionRepository options,
            final QuestionVersionTagRepository versionTags,
            final TagRepository tags,
            final SubjectRepository subjects,
            final ChapterRepository chapters,
            final AiVariantSourceRepository variantSources,
            final QuestionAuditService audit) {
        this.questions = questions;
        this.versions = versions;
        this.options = options;
        this.versionTags = versionTags;
        this.tags = tags;
        this.subjects = subjects;
        this.chapters = chapters;
        this.variantSources = variantSources;
        this.audit = audit;
    }

    @Transactional
    public UUID publish(final PublishCommand command) {
        Question.Type type = parseType(command.type());
        QuestionVersion.Difficulty difficulty = parseDifficulty(command.difficulty());
        validateSubjectChapter(command.subjectId(), command.chapterId());
        validateContent(type, command);
        QuestionVersion sourceVersion = versions.findById(command.sourceQuestionVersionId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Source question version not found"));
        Guard.require(sourceVersion.getStatus() == Question.Status.PUBLISHED,
                ErrorCode.STATE_CONFLICT, "Source question version is not published");
        Guard.require(versions.countByStemIgnoreCase(command.stem().trim()) == 0,
                ErrorCode.STATE_CONFLICT, "A question with this stem already exists");

        Question question = new Question();
        question.setSubjectId(command.subjectId());
        question.setChapterId(command.chapterId());
        question.setType(type);
        question.setStatus(Question.Status.PUBLISHED);
        question.setCreatedBy(command.actorId());
        questions.save(question);

        Instant now = Instant.now();
        QuestionVersion version = new QuestionVersion();
        version.setQuestionId(question.getId());
        version.setVersionNo(1);
        version.setStatus(Question.Status.PUBLISHED);
        version.setStem(command.stem().trim());
        version.setExplanation(command.explanation().trim());
        version.setFillReferenceAnswer(type == Question.Type.SINGLE_BLANK
                ? command.referenceAnswer().trim() : null);
        version.setDifficulty(difficulty);
        version.setSourceText("AI variant of question version " + command.sourceQuestionVersionId());
        version.setAuthorId(command.actorId());
        version.setReviewedBy(command.actorId());
        version.setReviewedAt(now);
        version.setPublishedAt(now);
        version.setContentHash(contentHash(command));
        versions.save(version);

        if (type == Question.Type.SINGLE_CHOICE) {
            int index = 0;
            for (OptionSpec option : command.options()) {
                QuestionOption entity = new QuestionOption();
                entity.setQuestionVersionId(version.getId());
                entity.setOptionKey(option.key().trim());
                entity.setContent(option.content().trim());
                entity.setSortOrder(index++);
                entity.setCorrect(option.correct());
                options.save(entity);
            }
        }
        attachTags(version.getId(), command.suggestedTags());

        question.setPublishedVersionId(version.getId());
        questions.save(question);

        AiVariantSource source = new AiVariantSource();
        source.setQuestionVersionId(version.getId());
        source.setSourceQuestionVersionId(command.sourceQuestionVersionId());
        source.setGenerationCandidateId(command.candidateId());
        source.setModelName(command.modelName());
        source.setPromptVersion(command.promptVersion());
        variantSources.save(source);

        audit.record(command.actorId(), "AI_VARIANT_PUBLISH", "QUESTION_VERSION", version.getId(),
                AdminAuditLog.Outcome.SUCCESS,
                Map.of("questionId", question.getId(),
                        "sourceVersionId", command.sourceQuestionVersionId(),
                        "candidateId", command.candidateId()));
        return version.getId();
    }

    private void validateSubjectChapter(final UUID subjectId, final UUID chapterId) {
        Subject subject = subjects.findById(subjectId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Subject not found"));
        if (chapterId == null) {
            return;
        }
        Chapter chapter = chapters.findById(chapterId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Chapter not found"));
        Guard.require(chapter.getSubjectId().equals(subject.getId()), ErrorCode.VALIDATION_FAILED,
                "Chapter does not belong to the selected subject");
    }

    private void validateContent(final Question.Type type, final PublishCommand command) {
        Guard.require(command.stem() != null && !command.stem().isBlank(),
                ErrorCode.VALIDATION_FAILED, "Generated stem is required");
        Guard.require(command.explanation() != null && !command.explanation().isBlank(),
                ErrorCode.VALIDATION_FAILED, "Generated explanation is required");
        if (type == Question.Type.SINGLE_CHOICE) {
            List<OptionSpec> optionSpecs = command.options();
            Guard.require(optionSpecs != null && optionSpecs.size() >= 2,
                    ErrorCode.VALIDATION_FAILED, "Single-choice questions need at least two options");
            Set<String> keys = new HashSet<>();
            int correct = 0;
            for (OptionSpec option : optionSpecs) {
                Guard.require(option.key() != null && !option.key().isBlank()
                                && option.content() != null && !option.content().isBlank(),
                        ErrorCode.VALIDATION_FAILED, "Option key and content are required");
                Guard.require(keys.add(option.key().trim()), ErrorCode.VALIDATION_FAILED,
                        "Option keys must be unique");
                if (option.correct()) {
                    correct++;
                }
            }
            Guard.require(correct == 1, ErrorCode.VALIDATION_FAILED,
                    "Single-choice questions need exactly one correct option");
        } else {
            Guard.require(command.referenceAnswer() != null
                            && !command.referenceAnswer().isBlank(),
                    ErrorCode.VALIDATION_FAILED, "Single-blank questions need a reference answer");
        }
    }

    private void attachTags(final UUID versionId, final List<String> suggestedTags) {
        if (suggestedTags == null) {
            return;
        }
        Set<UUID> linked = new HashSet<>();
        for (String raw : suggestedTags) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String normalized = TextNormalizer.normalizeUsername(raw.trim());
            Tag tag = tags.findByNormalizedName(normalized).orElse(null);
            if (tag == null || !linked.add(tag.getId())) {
                continue;
            }
            QuestionVersionTagId id = new QuestionVersionTagId();
            id.setQuestionVersionId(versionId);
            id.setTagId(tag.getId());
            QuestionVersionTag link = new QuestionVersionTag();
            link.setId(id);
            versionTags.save(link);
        }
    }

    private Question.Type parseType(final String value) {
        try {
            return Question.Type.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid generated question type", exception);
        }
    }

    private QuestionVersion.Difficulty parseDifficulty(final String value) {
        try {
            return QuestionVersion.Difficulty.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid generated difficulty", exception);
        }
    }

    private String contentHash(final PublishCommand command) {
        StringBuilder canonical = new StringBuilder()
                .append(command.stem().trim()).append('|')
                .append(command.explanation().trim()).append('|')
                .append(command.referenceAnswer() == null ? "" : command.referenceAnswer().trim())
                .append('|').append(command.difficulty());
        if (command.options() != null) {
            command.options().forEach(option -> canonical
                    .append('|').append(option.key().trim())
                    .append(':').append(option.content().trim())
                    .append(':').append(option.correct()));
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record OptionSpec(String key, String content, boolean correct) {
    }

    public record PublishCommand(
            UUID actorId,
            UUID subjectId,
            UUID chapterId,
            String stem,
            String type,
            List<OptionSpec> options,
            String referenceAnswer,
            String explanation,
            String difficulty,
            List<String> suggestedTags,
            UUID sourceQuestionVersionId,
            UUID candidateId,
            String modelName,
            String promptVersion) {
    }
}
