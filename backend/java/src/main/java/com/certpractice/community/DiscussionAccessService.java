package com.certpractice.community;

import java.util.UUID;

import com.certpractice.practice.PracticeSession;
import com.certpractice.practice.SessionItemRepository;
import com.certpractice.question.Question;
import com.certpractice.question.QuestionRepository;
import com.certpractice.question.QuestionVersion;
import com.certpractice.question.QuestionVersionRepository;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central access rules for question discussions. Discussions are hidden while
 * the current user has an in-progress practice session containing the question,
 * because discussion content can reveal the answer.
 */
@Service
public class DiscussionAccessService {

    private final QuestionRepository questions;
    private final QuestionVersionRepository versions;
    private final SessionItemRepository sessionItems;
    private final DiscussionPostRepository posts;

    public DiscussionAccessService(
            final QuestionRepository questions,
            final QuestionVersionRepository versions,
            final SessionItemRepository sessionItems,
            final DiscussionPostRepository posts) {
        this.questions = questions;
        this.versions = versions;
        this.sessionItems = sessionItems;
        this.posts = posts;
    }

    @Transactional(readOnly = true)
    public Question requireQuestion(final UUID questionId) {
        return questions.findById(questionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Question not found"));
    }

    @Transactional(readOnly = true)
    public QuestionVersion requireCurrentPublishedVersion(final UUID questionId) {
        Question question = requireQuestion(questionId);
        QuestionVersion version = versions
                .findByQuestionIdAndStatusAndSupersededAtIsNull(questionId, Question.Status.PUBLISHED)
                .orElse(null);
        if (version == null && question.getPublishedVersionId() != null) {
            version = versions.findById(question.getPublishedVersionId()).orElse(null);
        }
        if (version == null) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "Question has no published version");
        }
        return version;
    }

    public UUID currentPublishedVersionId(final UUID questionId) {
        return versions
                .findByQuestionIdAndStatusAndSupersededAtIsNull(questionId, Question.Status.PUBLISHED)
                .map(QuestionVersion::getId)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public void requireDiscussionAccess(final UUID userId, final UUID questionId) {
        requireQuestion(questionId);
        boolean blocked = sessionItems.existsByOwnerAndQuestionAndSessionStatus(
                userId, questionId, PracticeSession.Status.IN_PROGRESS);
        if (blocked) {
            throw new ApiException(
                    ErrorCode.STATE_CONFLICT,
                    "Discussions are unavailable while a practice session for this question is in progress");
        }
    }

    @Transactional(readOnly = true)
    public DiscussionPost requireVisiblePost(final UUID postId) {
        return posts.findByIdAndStatus(postId, DiscussionPost.Status.VISIBLE)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Discussion not found"));
    }
}
