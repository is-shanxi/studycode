package com.certpractice.question;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.certpractice.identity.AdminAuditLog;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.web.Guard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Draft review, publication and offline transitions. Publishing a new version
 * atomically moves the stable question pointer; old versions keep their
 * immutable content for historical records.
 */
@Service
public class QuestionLifecycleService {

    private final QuestionRepository questions;
    private final QuestionVersionRepository versions;
    private final ReviewEventRepository reviewEvents;
    private final QuestionAuditService audit;
    private final QuestionAuthoringService authoringService;

    public QuestionLifecycleService(
            final QuestionRepository questions,
            final QuestionVersionRepository versions,
            final ReviewEventRepository reviewEvents,
            final QuestionAuditService audit,
            final QuestionAuthoringService authoringService) {
        this.questions = questions;
        this.versions = versions;
        this.reviewEvents = reviewEvents;
        this.audit = audit;
        this.authoringService = authoringService;
    }

    @Transactional
    public QuestionDtos.QuestionStatusResponse submitReview(
            final UUID actorId,
            final UUID versionId,
            final QuestionDtos.ReviewRequest request) {
        QuestionVersion version = requireVersion(versionId);
        Guard.require(version.getStatus() == Question.Status.DRAFT, ErrorCode.STATE_CONFLICT,
                "Only draft versions can be submitted for review");

        Question question = requireQuestionForUpdate(version.getQuestionId());
        transition(version, actorId, Question.Status.PENDING_REVIEW, commentOf(request));
        if (question.getPublishedVersionId() == null) {
            question.setStatus(Question.Status.PENDING_REVIEW);
            questions.save(question);
        }

        audit.record(actorId, "QUESTION_SUBMIT_REVIEW", "QUESTION_VERSION", versionId,
                AdminAuditLog.Outcome.SUCCESS, Map.of("questionId", question.getId()));
        return new QuestionDtos.QuestionStatusResponse(question.getId(), versionId, version.getStatus().name());
    }

    @Transactional
    public QuestionDtos.QuestionStatusResponse approve(
            final UUID actorId,
            final UUID versionId,
            final QuestionDtos.ReviewRequest request) {
        QuestionVersion version = requireVersion(versionId);
        Guard.require(version.getStatus() == Question.Status.PENDING_REVIEW, ErrorCode.STATE_CONFLICT,
                "Only versions pending review can be approved");

        version.setReviewedBy(actorId);
        version.setReviewedAt(Instant.now());
        versions.save(version);

        boolean publishNow = request == null || request.publishNow() == null || request.publishNow();
        if (publishNow) {
            publishInternal(actorId, version, commentOf(request));
        } else {
            audit.record(actorId, "QUESTION_APPROVE", "QUESTION_VERSION", versionId,
                    AdminAuditLog.Outcome.SUCCESS, Map.of("publishNow", false));
        }
        return new QuestionDtos.QuestionStatusResponse(
                version.getQuestionId(), versionId, version.getStatus().name());
    }

    @Transactional
    public QuestionDtos.QuestionStatusResponse reject(
            final UUID actorId,
            final UUID versionId,
            final QuestionDtos.ReviewRequest request) {
        QuestionVersion version = requireVersion(versionId);
        Guard.require(version.getStatus() == Question.Status.PENDING_REVIEW, ErrorCode.STATE_CONFLICT,
                "Only versions pending review can be rejected");

        Question question = requireQuestionForUpdate(version.getQuestionId());
        transition(version, actorId, Question.Status.DRAFT, commentOf(request));
        if (question.getPublishedVersionId() == null) {
            question.setStatus(Question.Status.DRAFT);
            questions.save(question);
        }

        audit.record(actorId, "QUESTION_REJECT", "QUESTION_VERSION", versionId,
                AdminAuditLog.Outcome.REJECTED, Map.of("questionId", question.getId()));
        return new QuestionDtos.QuestionStatusResponse(question.getId(), versionId, version.getStatus().name());
    }

    @Transactional
    public QuestionDtos.QuestionStatusResponse publish(
            final UUID actorId,
            final UUID versionId,
            final QuestionDtos.ReviewRequest request) {
        QuestionVersion version = requireVersion(versionId);
        Guard.require(version.getStatus() == Question.Status.PENDING_REVIEW, ErrorCode.STATE_CONFLICT,
                "Only versions pending review can be published");

        version.setReviewedBy(actorId);
        version.setReviewedAt(Instant.now());
        publishInternal(actorId, version, commentOf(request));
        return new QuestionDtos.QuestionStatusResponse(
                version.getQuestionId(), versionId, version.getStatus().name());
    }

    @Transactional
    public QuestionDtos.QuestionStatusResponse offline(
            final UUID actorId,
            final UUID questionId,
            final QuestionDtos.ReviewRequest request) {
        Question question = requireQuestionForUpdate(questionId);
        Guard.require(question.getPublishedVersionId() != null, ErrorCode.STATE_CONFLICT,
                "Question is not published");

        QuestionVersion version = requireVersion(question.getPublishedVersionId());
        version.setStatus(Question.Status.OFFLINE);
        version.setOfflineAt(Instant.now());
        versions.save(version);

        question.setPublishedVersionId(null);
        question.setStatus(Question.Status.OFFLINE);
        questions.save(question);

        reviewEvents.save(reviewEvent(version.getId(), actorId,
                Question.Status.PUBLISHED, Question.Status.OFFLINE, commentOf(request)));
        audit.record(actorId, "QUESTION_OFFLINE", "QUESTION", questionId,
                AdminAuditLog.Outcome.SUCCESS, Map.of("versionId", version.getId()));
        return new QuestionDtos.QuestionStatusResponse(questionId, version.getId(), version.getStatus().name());
    }

    private void publishInternal(final UUID actorId, final QuestionVersion version, final String comment) {
        Question question = requireQuestionForUpdate(version.getQuestionId());
        // Validate after locking: validateForPublish loads the question entity, and
        // loading it before the lock would leave a stale managed copy in this
        // persistence context after a concurrent transaction commits.
        authoringService.validateForPublish(version);
        Instant now = Instant.now();
        UUID previousVersionId = question.getPublishedVersionId();
        if (previousVersionId != null && !previousVersionId.equals(version.getId())) {
            QuestionVersion previous = versions.findById(previousVersionId).orElse(null);
            if (previous != null) {
                previous.setSupersededAt(now);
                versions.saveAndFlush(previous);
            }
        }

        version.setStatus(Question.Status.PUBLISHED);
        version.setPublishedAt(now);
        version.setSupersededAt(null);
        if (version.getReviewedBy() == null) {
            version.setReviewedBy(actorId);
            version.setReviewedAt(now);
        }
        versions.save(version);

        question.setPublishedVersionId(version.getId());
        question.setStatus(Question.Status.PUBLISHED);
        questions.save(question);

        reviewEvents.save(reviewEvent(version.getId(), actorId,
                Question.Status.PENDING_REVIEW, Question.Status.PUBLISHED, comment));
        audit.record(actorId, "QUESTION_PUBLISH", "QUESTION_VERSION", version.getId(),
                AdminAuditLog.Outcome.SUCCESS, Map.of("questionId", question.getId()));
    }

    private void transition(
            final QuestionVersion version,
            final UUID actorId,
            final Question.Status target,
            final String comment) {
        Question.Status from = version.getStatus();
        version.setStatus(target);
        versions.save(version);
        reviewEvents.save(reviewEvent(version.getId(), actorId, from, target, comment));
    }

    private ReviewEvent reviewEvent(
            final UUID versionId,
            final UUID actorId,
            final Question.Status from,
            final Question.Status to,
            final String comment) {
        ReviewEvent event = new ReviewEvent();
        event.setQuestionVersionId(versionId);
        event.setActorUserId(actorId);
        event.setFromStatus(from);
        event.setToStatus(to);
        event.setComment(comment);
        return event;
    }

    private QuestionVersion requireVersion(final UUID versionId) {
        return versions.findById(versionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Question version not found"));
    }

    private Question requireQuestionForUpdate(final UUID questionId) {
        return questions.findByIdForUpdate(questionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Question not found"));
    }

    private String commentOf(final QuestionDtos.ReviewRequest request) {
        if (request == null || request.comment() == null || request.comment().isBlank()) {
            return null;
        }
        return request.comment().trim();
    }
}
