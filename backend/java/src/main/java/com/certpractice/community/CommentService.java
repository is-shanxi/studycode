package com.certpractice.community;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.certpractice.identity.AppUser;
import com.certpractice.identity.AppUserRepository;
import com.certpractice.notification.NotificationService;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-level comment threads. Replies may quote another comment in the same
 * thread but can never create a third nesting level.
 */
@Service
public class CommentService {

    private static final int QUOTE_PREVIEW_LENGTH = 100;

    private final DiscussionAccessService accessService;
    private final DiscussionCommentRepository comments;
    private final AppUserRepository users;
    private final ContentFilter contentFilter;
    private final NotificationService notificationService;

    public CommentService(
            final DiscussionAccessService accessService,
            final DiscussionCommentRepository comments,
            final AppUserRepository users,
            final ContentFilter contentFilter,
            final NotificationService notificationService) {
        this.accessService = accessService;
        this.comments = comments;
        this.users = users;
        this.contentFilter = contentFilter;
        this.notificationService = notificationService;
    }

    @Transactional
    public CommunityDtos.CommentView create(
            final UUID userId,
            final UUID postId,
            final CommunityDtos.CreateCommentRequest request) {
        DiscussionPost post = accessService.requireVisiblePost(postId);
        accessService.requireDiscussionAccess(userId, post.getQuestionId());

        UUID parentCommentId = request.parentCommentId();
        UUID quotedCommentId = request.quotedCommentId();
        DiscussionComment parent = null;
        DiscussionComment quoted = null;

        if (quotedCommentId != null) {
            quoted = requireVisibleComment(quotedCommentId, postId);
            if (parentCommentId == null) {
                parentCommentId = quoted.getParentCommentId() == null
                        ? quoted.getId()
                        : quoted.getParentCommentId();
            }
        }
        if (parentCommentId != null) {
            parent = requireVisibleComment(parentCommentId, postId);
            if (parent.getParentCommentId() != null) {
                throw new ApiException(
                        ErrorCode.STATE_CONFLICT,
                        "Comments support only one level of replies");
            }
            if (quoted != null) {
                UUID quotedThreadRootId = quoted.getParentCommentId() == null
                        ? quoted.getId()
                        : quoted.getParentCommentId();
                if (!quotedThreadRootId.equals(parent.getId())) {
                    throw new ApiException(
                            ErrorCode.STATE_CONFLICT,
                            "Quoted comment does not belong to the selected thread");
                }
            }
        }

        DiscussionComment comment = new DiscussionComment();
        comment.setPostId(postId);
        comment.setParentCommentId(parentCommentId);
        comment.setQuotedCommentId(quoted == null ? null : quoted.getId());
        comment.setAuthorUserId(userId);
        comment.setBody(contentFilter.sanitizeRequired(request.body(), "body"));
        comment.setStatus(DiscussionPost.Status.VISIBLE);
        comments.save(comment);

        Set<UUID> recipients = new LinkedHashSet<>();
        recipients.add(post.getAuthorUserId());
        if (parent != null) {
            recipients.add(parent.getAuthorUserId());
        }
        if (quoted != null) {
            recipients.add(quoted.getAuthorUserId());
        }
        for (UUID recipient : recipients) {
            notificationService.notifyCommentReply(
                    recipient, userId, post.getTitle(), post.getId(), comment.getId());
        }

        return new CommunityDtos.CommentView(
                comment.getId(),
                comment.getParentCommentId(),
                comment.getQuotedCommentId(),
                quoted == null ? null : preview(quoted.getBody()),
                comment.getAuthorUserId(),
                displayName(userId),
                comment.getBody(),
                comment.getStatus().name(),
                comment.getCreatedAt());
    }

    private DiscussionComment requireVisibleComment(final UUID commentId, final UUID postId) {
        DiscussionComment comment = comments.findById(commentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Comment not found"));
        if (!comment.getPostId().equals(postId)
                || comment.getStatus() != DiscussionPost.Status.VISIBLE) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Comment not found");
        }
        return comment;
    }

    private String displayName(final UUID userId) {
        return users.findById(userId)
                .map(this::displayName)
                .orElse("已注销用户");
    }

    private String displayName(final AppUser user) {
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        return user.getUsername();
    }

    private String preview(final String text) {
        if (text == null || text.length() <= QUOTE_PREVIEW_LENGTH) {
            return text;
        }
        return text.substring(0, QUOTE_PREVIEW_LENGTH) + "…";
    }
}
