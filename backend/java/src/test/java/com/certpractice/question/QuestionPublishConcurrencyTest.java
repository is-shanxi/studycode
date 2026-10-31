package com.certpractice.question;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.certpractice.support.P1TestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class QuestionPublishConcurrencyTest extends P1TestSupport {

    @Autowired
    private QuestionRepository questions;

    @Autowired
    private QuestionVersionRepository versions;

    @Test
    void concurrentPublishesLeaveExactlyOneCurrentVersion() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        QuestionRef draft = createDraft(catalog, "concurrent " + UUID.randomUUID());
        UUID questionId = draft.questionId();
        UUID firstVersionId = draft.versionId();

        QuestionDtos.AdminQuestionDetail cloned = authoringService.cloneVersion(ADMIN_ID, questionId);
        UUID secondVersionId = cloned.versions().stream()
                .map(QuestionDtos.VersionView::versionId)
                .filter(versionId -> !versionId.equals(firstVersionId))
                .findFirst()
                .orElseThrow();

        lifecycleService.submitReview(ADMIN_ID, firstVersionId, null);
        lifecycleService.submitReview(ADMIN_ID, secondVersionId, null);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> publishAfterSignal(ready, start, firstVersionId));
            Future<?> second = executor.submit(() -> publishAfterSignal(ready, start, secondVersionId));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        Question question = questions.findById(questionId).orElseThrow();
        List<QuestionVersion> current = versions.findByQuestionIdOrderByVersionNoDesc(questionId)
                .stream()
                .filter(version -> version.getStatus() == Question.Status.PUBLISHED)
                .filter(version -> version.getSupersededAt() == null)
                .toList();

        assertThat(current).hasSize(1);
        assertThat(question.getPublishedVersionId()).isEqualTo(current.get(0).getId());
    }

    private void publishAfterSignal(
            final CountDownLatch ready,
            final CountDownLatch start,
            final UUID versionId) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("publish start gate timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
        lifecycleService.approve(ADMIN_ID, versionId, new QuestionDtos.ReviewRequest(null, true));
    }

    private QuestionRef createDraft(final SubjectChapter catalog, final String stem) {
        QuestionDtos.AdminQuestionDetail detail = authoringService.createQuestion(ADMIN_ID,
                new QuestionDtos.CreateQuestionRequest(
                        catalog.subjectId(), catalog.chapterId(), "SINGLE_CHOICE", stem,
                        "Explanation for " + stem, null, "MEDIUM", "Test source",
                        List.of(
                                new QuestionDtos.OptionInput("A", "Option A", false),
                                new QuestionDtos.OptionInput("B", "Option B", true)),
                        List.of(), List.of()));
        return new QuestionRef(detail.questionId(), detail.versions().get(0).versionId());
    }
}
