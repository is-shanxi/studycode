package com.certpractice.selftest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.certpractice.practice.PracticeDtos;
import com.certpractice.practice.PracticeSession;
import com.certpractice.practice.QuestionSelectionService;
import com.certpractice.practice.SessionItem;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.web.Guard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-test composition: an approved-original and approved-AI-variant ratio
 * (default 6:4) with source-aware shortage fallback. The actual composition and
 * the ratios used are written into the session rule snapshot.
 */
@Service
public class SelfTestSelectionService {

    private static final double DEFAULT_ORIGINAL_RATIO = 0.6;
    private static final double DEFAULT_AI_VARIANT_RATIO = 0.4;

    private final QuestionSelectionService selectionService;

    public SelfTestSelectionService(final QuestionSelectionService selectionService) {
        this.selectionService = selectionService;
    }

    @Transactional(readOnly = true)
    public QuestionSelectionService.SelectionResult select(
            final UUID userId,
            final PracticeDtos.Filters filters,
            final int requestedCount,
            final PracticeDtos.CompositionSpec composition,
            final PracticeSession.Mode mode) {
        Ratios ratios = ratios(composition);
        QuestionSelectionService.CandidatePool pool =
                selectionService.loadPool(userId, filters, requestedCount, mode);
        Random random = new Random(pool.seed());

        List<QuestionSelectionService.Candidate> originals = pool.candidates().stream()
                .filter(candidate -> SessionItem.SourceType.ORIGINAL.name()
                        .equals(candidate.sourceType()))
                .toList();
        List<QuestionSelectionService.Candidate> variants = pool.candidates().stream()
                .filter(candidate -> SessionItem.SourceType.AI_VARIANT.name()
                        .equals(candidate.sourceType()))
                .toList();

        int targetOriginal = Math.min(requestedCount,
                (int) Math.round(requestedCount * ratios.original()));
        int targetVariant = requestedCount - targetOriginal;

        List<QuestionSelectionService.Candidate> selectedOriginals =
                new ArrayList<>(selectionService.pickWeighted(originals, targetOriginal, random));
        List<QuestionSelectionService.Candidate> selectedVariants =
                new ArrayList<>(selectionService.pickWeighted(variants, targetVariant, random));

        Set<UUID> chosen = new LinkedHashSet<>();
        selectedOriginals.forEach(candidate -> chosen.add(candidate.questionId()));
        selectedVariants.forEach(candidate -> chosen.add(candidate.questionId()));

        if (selectedVariants.size() < targetVariant) {
            int deficit = targetVariant - selectedVariants.size();
            List<QuestionSelectionService.Candidate> remaining = originals.stream()
                    .filter(candidate -> !chosen.contains(candidate.questionId()))
                    .toList();
            List<QuestionSelectionService.Candidate> fill =
                    selectionService.pickWeighted(remaining, deficit, random);
            selectedOriginals.addAll(fill);
            fill.forEach(candidate -> chosen.add(candidate.questionId()));
        }

        int missing = requestedCount - selectedOriginals.size() - selectedVariants.size();
        if (missing > 0) {
            List<QuestionSelectionService.Candidate> remaining = variants.stream()
                    .filter(candidate -> !chosen.contains(candidate.questionId()))
                    .toList();
            List<QuestionSelectionService.Candidate> fill =
                    selectionService.pickWeighted(remaining, missing, random);
            selectedVariants.addAll(fill);
        }

        List<QuestionSelectionService.Candidate> combined =
                new ArrayList<>(selectedOriginals.size() + selectedVariants.size());
        combined.addAll(selectedOriginals);
        combined.addAll(selectedVariants);
        Map<UUID, QuestionSelectionService.Candidate> unique = new LinkedHashMap<>();
        combined.forEach(candidate -> unique.putIfAbsent(candidate.questionId(), candidate));
        List<QuestionSelectionService.Candidate> finalSelection = new ArrayList<>(unique.values());
        Collections.shuffle(finalSelection, random);

        if (finalSelection.isEmpty()) {
            throw new ApiException(ErrorCode.NO_ELIGIBLE_QUESTIONS,
                    "No eligible questions match the filters");
        }

        int actualOriginal = countSource(finalSelection, SessionItem.SourceType.ORIGINAL);
        int actualVariant = countSource(finalSelection, SessionItem.SourceType.AI_VARIANT);
        int unseen = countBucket(finalSelection, QuestionSelectionService.Bucket.UNSEEN);
        int wrong = countBucket(finalSelection, QuestionSelectionService.Bucket.WRONG);
        int normal = countBucket(finalSelection, QuestionSelectionService.Bucket.NORMAL);

        Map<String, Object> snapshot = new LinkedHashMap<>(pool.baseSnapshot());
        snapshot.put("composition", Map.of(
                "originalRatio", ratios.original(),
                "aiVariantRatio", ratios.variant()));
        snapshot.put("targets", Map.of(
                "original", targetOriginal,
                "aiVariant", targetVariant));
        snapshot.put("actual", Map.of(
                "original", actualOriginal,
                "aiVariant", actualVariant));
        snapshot.put("buckets", Map.of("unseen", unseen, "wrong", wrong, "normal", normal));
        snapshot.put("fallback", finalSelection.size() < requestedCount);

        List<QuestionSelectionService.SelectedQuestion> items = new ArrayList<>();
        int ordinal = 1;
        for (QuestionSelectionService.Candidate candidate : finalSelection) {
            items.add(new QuestionSelectionService.SelectedQuestion(
                    candidate.questionId(),
                    candidate.versionId(),
                    candidate.sourceType(),
                    candidate.bucket().name(),
                    ordinal++));
        }
        return new QuestionSelectionService.SelectionResult(
                items, unseen, wrong, normal, pool.seed(), snapshot);
    }

    private Ratios ratios(final PracticeDtos.CompositionSpec composition) {
        double original = composition == null || composition.originalRatio() == null
                ? DEFAULT_ORIGINAL_RATIO
                : composition.originalRatio();
        double variant = composition == null || composition.aiVariantRatio() == null
                ? DEFAULT_AI_VARIANT_RATIO
                : composition.aiVariantRatio();
        Guard.require(Double.isFinite(original) && original >= 0 && original <= 1,
                ErrorCode.VALIDATION_FAILED,
                "composition.originalRatio must be between 0 and 1");
        Guard.require(Double.isFinite(variant) && variant >= 0 && variant <= 1,
                ErrorCode.VALIDATION_FAILED,
                "composition.aiVariantRatio must be between 0 and 1");
        double total = original + variant;
        Guard.require(total > 0, ErrorCode.VALIDATION_FAILED,
                "composition ratios must have a positive sum");
        return new Ratios(original / total, variant / total);
    }

    private int countSource(
            final List<QuestionSelectionService.Candidate> candidates,
            final SessionItem.SourceType sourceType) {
        return (int) candidates.stream()
                .filter(candidate -> sourceType.name().equals(candidate.sourceType()))
                .count();
    }

    private int countBucket(
            final List<QuestionSelectionService.Candidate> candidates,
            final QuestionSelectionService.Bucket bucket) {
        return (int) candidates.stream().filter(candidate -> candidate.bucket() == bucket).count();
    }

    private record Ratios(double original, double variant) {
    }
}
