package com.certpractice.practice;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.certpractice.question.AiVariantSourceRepository;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Controlled random question selection. Buckets default to 50/30/20
 * (unseen/wrong/normal) and difficulty to 30/50/20 (easy/medium/hard); both are
 * configurable through {@link PracticeSelectionProperties}. When a bucket is
 * short the remaining legal questions fill the gap. The random seed, weights and
 * actual composition are part of the session rule snapshot.
 */
@Service
public class QuestionSelectionService {

    private final EntityManager entityManager;
    private final WrongQuestionRepository wrongQuestions;
    private final AiVariantSourceRepository aiVariants;
    private final PracticeSelectionProperties selection;

    public QuestionSelectionService(
            final EntityManager entityManager,
            final WrongQuestionRepository wrongQuestions,
            final AiVariantSourceRepository aiVariants,
            final PracticeSelectionProperties selection) {
        this.entityManager = entityManager;
        this.wrongQuestions = wrongQuestions;
        this.aiVariants = aiVariants;
        this.selection = selection;
    }

    @Transactional(readOnly = true)
    public SelectionResult select(
            final UUID userId,
            final PracticeDtos.Filters filters,
            final int requestedCount,
            final PracticeSession.Mode mode) {
        CandidatePool pool = loadPool(userId, filters, requestedCount, mode);
        List<Candidate> candidates = pool.candidates();
        int[] quotas = quotas(requestedCount);
        Random random = new Random(pool.seed());
        List<Candidate> selected = new ArrayList<>();
        selected.addAll(pick(candidatesOfBucket(candidates, Bucket.UNSEEN), quotas[0], random));
        selected.addAll(pick(candidatesOfBucket(candidates, Bucket.WRONG), quotas[1], random));
        selected.addAll(pick(candidatesOfBucket(candidates, Bucket.NORMAL), quotas[2], random));

        if (selected.size() < Math.min(requestedCount, candidates.size())) {
            Set<UUID> chosen = new HashSet<>();
            selected.forEach(candidate -> chosen.add(candidate.questionId()));
            List<Candidate> remaining = candidates.stream()
                    .filter(candidate -> !chosen.contains(candidate.questionId()))
                    .sorted(Comparator.comparing(Candidate::questionId))
                    .toList();
            selected.addAll(pick(remaining,
                    Math.min(requestedCount, candidates.size()) - selected.size(), random));
        }

        if (selected.isEmpty()) {
            throw new ApiException(ErrorCode.NO_ELIGIBLE_QUESTIONS, "No eligible questions match the filters");
        }

        int unseenCount = countBucket(selected, Bucket.UNSEEN);
        int wrongCount = countBucket(selected, Bucket.WRONG);
        int normalCount = countBucket(selected, Bucket.NORMAL);

        Map<String, Object> ruleSnapshot = new LinkedHashMap<>(pool.baseSnapshot());
        ruleSnapshot.put("quotas", Map.of("unseen", quotas[0], "wrong", quotas[1], "normal", quotas[2]));
        ruleSnapshot.put("actual", Map.of(
                "unseen", unseenCount, "wrong", wrongCount, "normal", normalCount));
        ruleSnapshot.put("fallback", selected.size() < requestedCount);

        return new SelectionResult(
                toSelected(selected), unseenCount, wrongCount, normalCount, pool.seed(), ruleSnapshot);
    }

    /**
     * Loads and classifies the legal candidate pool once so both normal and
     * self-test selection share the same history weights and source typing.
     */
    @Transactional(readOnly = true)
    public CandidatePool loadPool(
            final UUID userId,
            final PracticeDtos.Filters filters,
            final int requestedCount,
            final PracticeSession.Mode mode) {
        List<Candidate> candidates = loadCandidates(filters);
        if (candidates.isEmpty()) {
            throw new ApiException(ErrorCode.NO_ELIGIBLE_QUESTIONS, "No eligible questions match the filters");
        }

        int historyDays = filters == null || filters.historyDays() == null ? 90 : filters.historyDays();
        if (historyDays < 1) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "historyDays must be positive");
        }
        Instant since = Instant.now().minus(historyDays, ChronoUnit.DAYS);
        Set<UUID> wrongIds = new HashSet<>();
        wrongQuestions.findActiveSince(userId, since)
                .forEach(wrong -> wrongIds.add(wrong.getQuestionId()));
        Set<UUID> answeredIds = loadAnsweredQuestionIds(userId, since);
        Set<UUID> aiVariantVersionIds = aiVariants.findAllVersionIds();

        List<Candidate> classified = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) {
            String sourceType = aiVariantVersionIds.contains(candidate.versionId())
                    ? SessionItem.SourceType.AI_VARIANT.name()
                    : SessionItem.SourceType.ORIGINAL.name();
            Bucket bucket = wrongIds.contains(candidate.questionId())
                    ? Bucket.WRONG
                    : answeredIds.contains(candidate.questionId()) ? Bucket.NORMAL : Bucket.UNSEEN;
            classified.add(new Candidate(
                    candidate.questionId(), candidate.versionId(), candidate.difficulty(),
                    bucket, sourceType));
        }

        long seed = ThreadLocalRandom.current().nextLong();
        Map<String, Object> baseSnapshot = new LinkedHashMap<>();
        baseSnapshot.put("version", 1);
        baseSnapshot.put("mode", mode.name());
        baseSnapshot.put("requestedCount", requestedCount);
        baseSnapshot.put("historyDays", historyDays);
        baseSnapshot.put("seed", seed);
        baseSnapshot.put("bucketWeights", Map.of(
                "unseen", selection.getUnseenWeight(),
                "wrong", selection.getWrongWeight(),
                "normal", selection.getNormalWeight()));
        baseSnapshot.put("difficultyWeights", selection.difficultyWeights());
        baseSnapshot.put("maxCandidates", selection.getMaxCandidates());
        return new CandidatePool(classified, historyDays, seed, baseSnapshot);
    }

    /**
     * Weighted sampling used by the self-test composition service. The caller
     * owns the random source so the seed remains part of the rule snapshot.
     */
    public List<Candidate> pickWeighted(
            final List<Candidate> pool,
            final int count,
            final Random random) {
        return pick(pool, count, random);
    }

    private List<Candidate> candidatesOfBucket(final List<Candidate> candidates, final Bucket bucket) {
        return candidates.stream().filter(candidate -> candidate.bucket() == bucket).toList();
    }

    private int countBucket(final List<Candidate> candidates, final Bucket bucket) {
        return (int) candidates.stream().filter(candidate -> candidate.bucket() == bucket).count();
    }

    private List<SelectedQuestion> toSelected(final List<Candidate> selected) {
        List<SelectedQuestion> items = new ArrayList<>(selected.size());
        int ordinal = 1;
        for (Candidate candidate : selected) {
            items.add(new SelectedQuestion(
                    candidate.questionId(),
                    candidate.versionId(),
                    candidate.sourceType(),
                    candidate.bucket().name(),
                    ordinal++));
        }
        return items;
    }

    private List<Candidate> loadCandidates(final PracticeDtos.Filters filters) {
        StringBuilder sql = new StringBuilder("""
                SELECT q.id, q.published_version_id, v.difficulty
                FROM question q
                JOIN question_version v ON v.id = q.published_version_id
                WHERE q.status = 'PUBLISHED' AND v.status = 'PUBLISHED'
                """);
        Map<String, Object> bindings = new HashMap<>();
        if (filters != null) {
            if (filters.subjectId() != null) {
                sql.append(" AND q.subject_id = :subjectId");
                bindings.put("subjectId", filters.subjectId());
            }
            if (filters.certificateId() != null) {
                sql.append(" AND EXISTS (SELECT 1 FROM subject s WHERE s.id = q.subject_id"
                        + " AND s.certificate_id = :certificateId)");
                bindings.put("certificateId", filters.certificateId());
            }
            if (filters.chapterIds() != null && !filters.chapterIds().isEmpty()) {
                sql.append(" AND q.chapter_id IN (:chapterIds)");
                bindings.put("chapterIds", filters.chapterIds());
            }
            if (filters.difficulty() != null && !filters.difficulty().isEmpty()) {
                sql.append(" AND v.difficulty IN (:difficulties)");
                bindings.put("difficulties", filters.difficulty());
            }
            if (filters.tagIds() != null && !filters.tagIds().isEmpty()) {
                sql.append("""
                         AND (SELECT COUNT(DISTINCT vt.tag_id) FROM question_version_tag vt
                              WHERE vt.question_version_id = v.id AND vt.tag_id IN (:tagIds)) = :tagCount
                        """);
                bindings.put("tagIds", filters.tagIds());
                bindings.put("tagCount", (long) filters.tagIds().size());
            }
        }
        sql.append(" ORDER BY q.id LIMIT :maxCandidates");
        bindings.put("maxCandidates", selection.getMaxCandidates());

        Query query = entityManager.createNativeQuery(sql.toString(), Tuple.class);
        bindings.forEach(query::setParameter);
        List<Candidate> candidates = new ArrayList<>();
        for (Object raw : query.getResultList()) {
            Tuple tuple = (Tuple) raw;
            candidates.add(new Candidate(
                    tuple.get(0, UUID.class),
                    tuple.get(1, UUID.class),
                    tuple.get(2, String.class),
                    Bucket.UNSEEN,
                    null));
        }
        return candidates;
    }

    private Set<UUID> loadAnsweredQuestionIds(final UUID userId, final Instant since) {
        Query query = entityManager.createNativeQuery("""
                SELECT DISTINCT si.question_id
                FROM session_item si
                JOIN practice_session ps ON ps.id = si.session_id
                WHERE ps.user_id = :userId
                  AND ps.created_at >= :since
                  AND ps.status IN ('SUBMITTED', 'PENDING_SELF_ASSESSMENT', 'COMPLETED')
                """);
        query.setParameter("userId", userId);
        query.setParameter("since", since);
        Set<UUID> ids = new HashSet<>();
        for (Object value : query.getResultList()) {
            ids.add(value instanceof UUID uuid ? uuid : UUID.fromString(value.toString()));
        }
        return ids;
    }

    private List<Candidate> pick(final List<Candidate> pool, final int count, final Random random) {
        if (count <= 0 || pool.isEmpty()) {
            return List.of();
        }
        List<Candidate> remaining = new ArrayList<>(pool);
        List<Candidate> picked = new ArrayList<>();
        while (picked.size() < count && !remaining.isEmpty()) {
            double totalWeight = 0;
            for (Candidate candidate : remaining) {
                totalWeight += weight(candidate);
            }
            double target = random.nextDouble() * totalWeight;
            int index = 0;
            double running = 0;
            for (int current = 0; current < remaining.size(); current++) {
                running += weight(remaining.get(current));
                if (running >= target) {
                    index = current;
                    break;
                }
            }
            picked.add(remaining.remove(index));
        }
        return picked;
    }

    private double weight(final Candidate candidate) {
        double bucket = bucketWeight(candidate.bucket());
        double difficulty = selection.difficultyWeight(candidate.difficulty());
        return bucket * difficulty;
    }

    private double bucketWeight(final Bucket bucket) {
        return switch (bucket) {
            case UNSEEN -> selection.getUnseenWeight();
            case WRONG -> selection.getWrongWeight();
            case NORMAL -> selection.getNormalWeight();
        };
    }

    private int[] quotas(final int requestedCount) {
        return quotas(requestedCount,
                selection.getUnseenWeight(),
                selection.getWrongWeight(),
                selection.getNormalWeight());
    }

    static int[] quotas(
            final int requestedCount,
            final double unseenWeight,
            final double wrongWeight,
            final double normalWeight) {
        if (unseenWeight + wrongWeight + normalWeight <= 0) {
            throw new IllegalArgumentException("bucket weights must sum to a positive value");
        }
        double[] ratios = {unseenWeight, wrongWeight, normalWeight};
        int[] quotas = new int[3];
        double[] fractions = new double[3];
        int used = 0;
        for (int index = 0; index < ratios.length; index++) {
            double raw = requestedCount * ratios[index];
            quotas[index] = (int) Math.floor(raw);
            fractions[index] = raw - quotas[index];
            used += quotas[index];
        }
        while (used < requestedCount) {
            int best = 0;
            for (int index = 1; index < fractions.length; index++) {
                if (fractions[index] > fractions[best]) {
                    best = index;
                }
            }
            quotas[best]++;
            fractions[best] = -1;
            used++;
        }
        return quotas;
    }

    public enum Bucket {
        UNSEEN,
        WRONG,
        NORMAL
    }

    public record Candidate(
            UUID questionId,
            UUID versionId,
            String difficulty,
            Bucket bucket,
            String sourceType) {
    }

    public record CandidatePool(
            List<Candidate> candidates,
            int historyDays,
            long seed,
            Map<String, Object> baseSnapshot) {
    }

    public record SelectedQuestion(
            UUID questionId,
            UUID versionId,
            String sourceType,
            String selectionBucket,
            int ordinal) {
    }

    public record SelectionResult(
            List<SelectedQuestion> items,
            int unseenCount,
            int wrongCount,
            int normalCount,
            long seed,
            Map<String, Object> ruleSnapshot) {
    }
}
