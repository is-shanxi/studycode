package com.certpractice.question;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.certpractice.catalog.Chapter;
import com.certpractice.catalog.ChapterRepository;
import com.certpractice.catalog.Tag;
import com.certpractice.catalog.TagRepository;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public query port used by the AI generation scheduler. It resolves stable
 * question identities to their exact published versions and catalog labels.
 */
@Service
public class QuestionGenerationQueryService {

    private final QuestionRepository questions;
    private final QuestionVersionRepository versions;
    private final ChapterRepository chapters;
    private final QuestionVersionTagRepository versionTags;
    private final TagRepository tags;

    public QuestionGenerationQueryService(
            final QuestionRepository questions,
            final QuestionVersionRepository versions,
            final ChapterRepository chapters,
            final QuestionVersionTagRepository versionTags,
            final TagRepository tags) {
        this.questions = questions;
        this.versions = versions;
        this.chapters = chapters;
        this.versionTags = versionTags;
        this.tags = tags;
    }

    @Transactional(readOnly = true)
    public List<GenerationSource> load(final List<UUID> questionIds) {
        List<GenerationSource> sources = new ArrayList<>(questionIds.size());
        for (UUID questionId : questionIds) {
            Question question = questions.findByIdAndStatus(questionId, Question.Status.PUBLISHED)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                            "Published source question not found"));
            QuestionVersion version = versions.findById(question.getPublishedVersionId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                            "Published source version not found"));
            String chapterName = question.getChapterId() == null ? null
                    : chapters.findById(question.getChapterId())
                            .map(Chapter::getName)
                            .orElse(null);
            List<UUID> tagIds = versionTags.findByIdQuestionVersionId(version.getId()).stream()
                    .map(link -> link.getId().getTagId())
                    .toList();
            List<String> tagNames = tags.findAllById(tagIds).stream()
                    .map(Tag::getName)
                    .toList();
            sources.add(new GenerationSource(
                    question.getId(),
                    question.getSubjectId(),
                    version.getId(),
                    version.getStem(),
                    question.getType().name(),
                    chapterName,
                    tagNames));
        }
        return sources;
    }

    public record GenerationSource(
            UUID questionId,
            UUID subjectId,
            UUID versionId,
            String stem,
            String type,
            String chapter,
            List<String> tags) {
    }
}
