package com.certpractice.question;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.certpractice.catalog.ChapterRepository;
import com.certpractice.catalog.Tag;
import com.certpractice.catalog.TagRepository;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.web.PageQuery;
import com.certpractice.shared.web.PageResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Search is deliberately field-scoped: public keyword search only looks at the
 * stem, chapter name and tag names. Options, reference answers, aliases and
 * explanations are never part of the public search predicate.
 */
@Service
public class QuestionSearchService {

    private final EntityManager entityManager;
    private final ChapterRepository chapters;
    private final TagRepository tags;
    private final QuestionVersionTagRepository versionTags;
    private final QuestionVersionRepository versions;
    private final QuestionRepository questions;
    private final QuestionOptionRepository options;
    private final QuestionAnswerAliasRepository aliases;

    public QuestionSearchService(
            final EntityManager entityManager,
            final ChapterRepository chapters,
            final TagRepository tags,
            final QuestionVersionTagRepository versionTags,
            final QuestionVersionRepository versions,
            final QuestionRepository questions,
            final QuestionOptionRepository options,
            final QuestionAnswerAliasRepository aliases) {
        this.entityManager = entityManager;
        this.chapters = chapters;
        this.tags = tags;
        this.versionTags = versionTags;
        this.versions = versions;
        this.questions = questions;
        this.options = options;
        this.aliases = aliases;
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestionDtos.QuestionSummary> searchPublic(
            final UUID certificateId,
            final UUID subjectId,
            final List<UUID> chapterIds,
            final List<UUID> tagIds,
            final String keyword,
            final List<String> difficulties,
            final String type,
            final PageQuery pageQuery) {
        List<UUID> safeChapterIds = chapterIds == null ? List.of() : chapterIds;
        List<UUID> safeTagIds = tagIds == null ? List.of() : tagIds;
        List<String> safeDifficulties = difficulties == null ? List.of() : difficulties;

        StringBuilder from = new StringBuilder("""
                FROM question q
                JOIN question_version v ON v.id = q.published_version_id
                LEFT JOIN chapter ch ON ch.id = q.chapter_id
                WHERE q.status = 'PUBLISHED' AND v.status = 'PUBLISHED'
                """);
        List<Object[]> bindings = new ArrayList<>();
        appendPublicFilters(from, bindings, certificateId, subjectId, safeChapterIds, safeTagIds,
                keyword, safeDifficulties, type);

        String countSql = "SELECT COUNT(*) " + from;
        Query countQuery = entityManager.createNativeQuery(countSql);
        bind(bindings, countQuery);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        String dataSql = "SELECT q.id, q.published_version_id, q.type, q.status, v.stem, v.difficulty, "
                + "q.chapter_id, ch.name " + from + " ORDER BY q.updated_at DESC, q.id LIMIT :limit OFFSET :offset";
        Query dataQuery = entityManager.createNativeQuery(dataSql, Tuple.class);
        bind(bindings, dataQuery);
        dataQuery.setParameter("limit", pageQuery.size());
        dataQuery.setParameter("offset", (long) pageQuery.page() * pageQuery.size());

        List<QuestionDtos.QuestionSummary> items = new ArrayList<>();
        for (Object raw : dataQuery.getResultList()) {
            Tuple tuple = (Tuple) raw;
            UUID versionId = tuple.get(1, UUID.class);
            items.add(new QuestionDtos.QuestionSummary(
                    tuple.get(0, UUID.class),
                    versionId,
                    tuple.get(2, String.class),
                    tuple.get(4, String.class),
                    tuple.get(5, String.class),
                    chapterRef(tuple.get(6, UUID.class), tuple.get(7, String.class)),
                    tagRefs(versionId),
                    tuple.get(3, String.class)));
        }
        return new PageResponse<>(items, pageQuery.page(), pageQuery.size(), total);
    }

    @Transactional(readOnly = true)
    public QuestionDtos.PublicQuestionView getPublicQuestion(final UUID questionId) {
        Question question = questions.findById(questionId)
                .orElseThrow(() -> notFound());
        if (question.getStatus() != Question.Status.PUBLISHED || question.getPublishedVersionId() == null) {
            throw notFound();
        }
        QuestionVersion version = versions.findById(question.getPublishedVersionId())
                .orElseThrow(QuestionSearchService::notFound);
        List<QuestionDtos.OptionSafe> safeOptions = options
                .findByQuestionVersionIdOrderBySortOrderAsc(version.getId())
                .stream()
                .map(option -> new QuestionDtos.OptionSafe(
                        option.getId(),
                        option.getOptionKey(),
                        option.getContent()))
                .toList();
        return new QuestionDtos.PublicQuestionView(
                question.getId(),
                version.getId(),
                question.getType().name(),
                version.getStem(),
                version.getDifficulty().name(),
                chapterRef(question.getChapterId()),
                tagRefs(version.getId()),
                safeOptions);
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestionDtos.AdminQuestionSummary> searchAdmin(
            final String keyword,
            final String status,
            final String type,
            final UUID subjectId,
            final PageQuery pageQuery) {
        StringBuilder from = new StringBuilder("""
                FROM question q
                LEFT JOIN LATERAL (
                    SELECT v2.id, v2.stem, v2.difficulty, v2.explanation
                    FROM question_version v2
                    WHERE v2.question_id = q.id
                    ORDER BY v2.version_no DESC
                    LIMIT 1
                ) v ON true
                WHERE 1 = 1
                """);
        List<Object[]> bindings = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            from.append(" AND q.status = :status");
            bindings.add(new Object[] {"status", status});
        }
        if (type != null && !type.isBlank()) {
            from.append(" AND q.type = :type");
            bindings.add(new Object[] {"type", type});
        }
        if (subjectId != null) {
            from.append(" AND q.subject_id = :subjectId");
            bindings.add(new Object[] {"subjectId", subjectId});
        }
        if (keyword != null && !keyword.isBlank()) {
            from.append("""
                     AND (v.stem ILIKE :keyword
                          OR v.explanation ILIKE :keyword
                          OR EXISTS (SELECT 1 FROM question_option o
                                     WHERE o.question_version_id = v.id AND o.content ILIKE :keyword)
                          OR EXISTS (SELECT 1 FROM question_answer_alias a
                                     WHERE a.question_version_id = v.id AND a.value ILIKE :keyword))
                    """);
            bindings.add(new Object[] {"keyword", like(keyword)});
        }

        Query countQuery = entityManager.createNativeQuery("SELECT COUNT(*) " + from);
        bind(bindings, countQuery);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        String dataSql = "SELECT q.id, v.id, q.type, q.status, v.stem, v.difficulty, q.updated_at "
                + from + " ORDER BY q.updated_at DESC, q.id LIMIT :limit OFFSET :offset";
        Query dataQuery = entityManager.createNativeQuery(dataSql, Tuple.class);
        bind(bindings, dataQuery);
        dataQuery.setParameter("limit", pageQuery.size());
        dataQuery.setParameter("offset", (long) pageQuery.page() * pageQuery.size());

        List<QuestionDtos.AdminQuestionSummary> items = new ArrayList<>();
        for (Object raw : dataQuery.getResultList()) {
            Tuple tuple = (Tuple) raw;
            items.add(new QuestionDtos.AdminQuestionSummary(
                    tuple.get(0, UUID.class),
                    tuple.get(1, UUID.class),
                    tuple.get(2, String.class),
                    tuple.get(3, String.class),
                    tuple.get(4, String.class),
                    tuple.get(5, String.class),
                    toInstant(tuple.get(6))));
        }
        return new PageResponse<>(items, pageQuery.page(), pageQuery.size(), total);
    }

    @Transactional(readOnly = true)
    public QuestionDtos.AdminQuestionDetail getAdminQuestion(final UUID questionId) {
        Question question = questions.findById(questionId)
                .orElseThrow(QuestionSearchService::notFound);
        List<QuestionDtos.VersionView> versionViews = versions.findByQuestionIdOrderByVersionNoDesc(questionId)
                .stream()
                .map(this::toVersionView)
                .toList();
        return new QuestionDtos.AdminQuestionDetail(
                question.getId(),
                question.getSubjectId(),
                question.getChapterId(),
                question.getType().name(),
                question.getStatus().name(),
                question.getPublishedVersionId(),
                versionViews);
    }

    @Transactional(readOnly = true)
    public QuestionDtos.VersionView toVersionView(final QuestionVersion version) {
        List<QuestionDtos.OptionAdmin> optionViews = options
                .findByQuestionVersionIdOrderBySortOrderAsc(version.getId())
                .stream()
                .map(option -> new QuestionDtos.OptionAdmin(
                        option.getId(),
                        option.getOptionKey(),
                        option.getContent(),
                        option.isCorrect()))
                .toList();
        List<String> aliasValues = aliases.findByQuestionVersionId(version.getId())
                .stream()
                .map(QuestionAnswerAlias::getValue)
                .sorted()
                .toList();
        return new QuestionDtos.VersionView(
                version.getId(),
                version.getVersionNo(),
                version.getStatus().name(),
                version.getStem(),
                version.getExplanation(),
                version.getFillReferenceAnswer(),
                version.getDifficulty().name(),
                version.getSourceText(),
                version.getAuthorId(),
                version.getReviewedBy(),
                version.getReviewedAt(),
                version.getPublishedAt(),
                version.getOfflineAt(),
                version.getSupersededAt(),
                optionViews,
                aliasValues,
                tagRefs(version.getId()));
    }

    public List<QuestionDtos.TagRef> tagRefs(final UUID versionId) {
        List<UUID> tagIds = versionTags.findByIdQuestionVersionId(versionId)
                .stream()
                .map(tag -> tag.getId().getTagId())
                .toList();
        return tags.findAllById(tagIds).stream()
                .sorted(Comparator.comparing(Tag::getName))
                .map(tag -> new QuestionDtos.TagRef(tag.getId(), tag.getName()))
                .toList();
    }

    public QuestionDtos.ChapterRef chapterRef(final UUID chapterId) {
        if (chapterId == null) {
            return null;
        }
        return chapters.findById(chapterId)
                .map(chapter -> new QuestionDtos.ChapterRef(chapter.getId(), chapter.getName()))
                .orElse(null);
    }

    private QuestionDtos.ChapterRef chapterRef(final UUID chapterId, final String chapterName) {
        if (chapterId == null) {
            return null;
        }
        return new QuestionDtos.ChapterRef(chapterId, chapterName);
    }

    private void appendPublicFilters(
            final StringBuilder sql,
            final List<Object[]> bindings,
            final UUID certificateId,
            final UUID subjectId,
            final List<UUID> chapterIds,
            final List<UUID> tagIds,
            final String keyword,
            final List<String> difficulties,
            final String type) {
        if (subjectId != null) {
            sql.append(" AND q.subject_id = :subjectId");
            bindings.add(new Object[] {"subjectId", subjectId});
        }
        if (certificateId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM subject s WHERE s.id = q.subject_id"
                    + " AND s.certificate_id = :certificateId)");
            bindings.add(new Object[] {"certificateId", certificateId});
        }
        if (!chapterIds.isEmpty()) {
            sql.append(" AND q.chapter_id IN (:chapterIds)");
            bindings.add(new Object[] {"chapterIds", chapterIds});
        }
        if (type != null && !type.isBlank()) {
            sql.append(" AND q.type = :type");
            bindings.add(new Object[] {"type", type});
        }
        if (!difficulties.isEmpty()) {
            sql.append(" AND v.difficulty IN (:difficulties)");
            bindings.add(new Object[] {"difficulties", difficulties});
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append("""
                     AND (v.stem ILIKE :keyword
                          OR ch.name ILIKE :keyword
                          OR EXISTS (SELECT 1 FROM question_version_tag vt
                                     JOIN tag t ON t.id = vt.tag_id
                                     WHERE vt.question_version_id = v.id AND t.name ILIKE :keyword))
                    """);
            bindings.add(new Object[] {"keyword", like(keyword)});
        }
        if (!tagIds.isEmpty()) {
            sql.append("""
                     AND (SELECT COUNT(DISTINCT vt2.tag_id) FROM question_version_tag vt2
                          WHERE vt2.question_version_id = v.id AND vt2.tag_id IN (:tagIds)) = :tagCount
                    """);
            bindings.add(new Object[] {"tagIds", tagIds});
            bindings.add(new Object[] {"tagCount", (long) tagIds.size()});
        }
    }

    private void bind(final List<Object[]> bindings, final Query query) {
        for (Object[] binding : bindings) {
            query.setParameter((String) binding[0], binding[1]);
        }
    }

    private String like(final String keyword) {
        return "%" + keyword.trim() + "%";
    }

    private Instant toInstant(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return null;
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, "Question not found");
    }
}
