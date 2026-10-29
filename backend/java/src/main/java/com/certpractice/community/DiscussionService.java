package com.certpractice.community;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.certpractice.identity.AppUser;
import com.certpractice.identity.AppUserRepository;
import com.certpractice.question.QuestionVersion;
import com.certpractice.question.QuestionVersionRepository;
import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.web.Guard;
import com.certpractice.shared.web.PageQuery;
import com.certpractice.shared.web.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Version-bound discussion posts. New posts must bind the current published
 * version; existing posts keep their original version label.
 */
@Service
public class DiscussionService {

    private static final int BODY_PREVIEW_LENGTH = 120;

    private final DiscussionAccessService accessService;
    private final DiscussionPostRepository posts;
    private final DiscussionCommentRepository comments;
    private final QuestionVersionRepository versions;
    private final AppUserRepository users;
    private final ContentFilter contentFilter;

    public DiscussionService(
            final DiscussionAccessService accessService,
            final DiscussionPostRepository posts,
            final DiscussionCommentRepository comments,
            final QuestionVersionRepository versions,
            final AppUserRepository users,
            final ContentFilter contentFilter) {
        this.accessService = accessService;
        this.posts = posts;
        this.comments = comments;
        this.versions = versions;
        this.users = users;
        this.contentFilter = contentFilter;
    }

    @Transactional(readOnly = true)
    public PageResponse<CommunityDtos.PostSummary> list(
            final UUID userId,
            final UUID questionId,
            final PageQuery pageQuery) {
        accessService.requireDiscussionAccess(userId, questionId);

        Page<DiscussionPost> page = posts.findByQuestionIdAndStatusOrderByCreatedAtDesc(
                questionId, DiscussionPost.Status.VISIBLE, pageQuery.toPageable());
        Map<UUID, QuestionVersion> versionMap = versions.findAllById(
                        page.getContent().stream().map(DiscussionPost::getQuestionVersionId).toList())
                .stream()
                .collect(Collectors.toMap(QuestionVersion::getId, Function.identity()));
        Map<UUID, String> authorNames = authorNames(
                page.getContent().stream().map(DiscussionPost::getAuthorUserId).toList());
        UUID currentVersionId = accessService.currentPublishedVersionId(questionId);

        List<CommunityDtos.PostSummary> items = page.getContent().stream()
                .map(post -> toSummary(post, versionMap.get(post.getQuestionVersionId()),
                        currentVersionId, authorNames))
                .toList();
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public CommunityDtos.PostDetail detail(final UUID userId, final UUID postId) {
        DiscussionPost post = accessService.requireVisiblePost(postId);
        accessService.requireDiscussionAccess(userId, post.getQuestionId());

        QuestionVersion version = versions.findById(post.getQuestionVersionId()).orElse(null);
        UUID currentVersionId = accessService.currentPublishedVersionId(post.getQuestionId());
        List<DiscussionComment> visibleComments = comments.findByPostIdAndStatusOrderByCreatedAtAsc(
                postId, DiscussionPost.Status.VISIBLE);
        Map<UUID, DiscussionComment> commentMap = visibleComments.stream()
                .collect(Collectors.toMap(DiscussionComment::getId, Function.identity()));

        List<UUID> authorIds = new ArrayList<>();
        authorIds.add(post.getAuthorUserId());
        visibleComments.forEach(comment -> authorIds.add(comment.getAuthorUserId()));
        Map<UUID, String> authorNames = authorNames(authorIds);

        List<CommunityDtos.CommentView> commentViews = visibleComments.stream()
                .map(comment -> toCommentView(comment, commentMap, authorNames))
                .toList();

        return new CommunityDtos.PostDetail(
                post.getId(),
                post.getQuestionId(),
                post.getQuestionVersionId(),
                version == null ? 0 : version.getVersionNo(),
                post.getQuestionVersionId().equals(currentVersionId),
                post.getTitle(),
                post.getBody(),
                post.getAuthorUserId(),
                authorNames.getOrDefault(post.getAuthorUserId(), "已注销用户"),
                post.getStatus().name(),
                post.getCreatedAt(),
                commentViews);
    }

    @Transactional
    public CommunityDtos.PostSummary create(
            final UUID userId,
            final UUID questionId,
            final CommunityDtos.CreatePostRequest request) {
        accessService.requireDiscussionAccess(userId, questionId);
        QuestionVersion currentVersion = accessService.requireCurrentPublishedVersion(questionId);
        Guard.require(request.questionVersionId().equals(currentVersion.getId()),
                ErrorCode.STATE_CONFLICT,
                "Discussions must bind the current published question version");

        DiscussionPost post = new DiscussionPost();
        post.setQuestionId(questionId);
        post.setQuestionVersionId(currentVersion.getId());
        post.setAuthorUserId(userId);
        post.setTitle(contentFilter.sanitizeRequired(request.title(), "title"));
        post.setBody(contentFilter.sanitizeRequired(request.body(), "body"));
        post.setStatus(DiscussionPost.Status.VISIBLE);
        posts.save(post);

        return toSummary(post, currentVersion, currentVersion.getId(), authorNames(List.of(userId)));
    }

    private CommunityDtos.PostSummary toSummary(
            final DiscussionPost post,
            final QuestionVersion version,
            final UUID currentVersionId,
            final Map<UUID, String> authorNames) {
        return new CommunityDtos.PostSummary(
                post.getId(),
                post.getQuestionId(),
                post.getQuestionVersionId(),
                version == null ? 0 : version.getVersionNo(),
                post.getQuestionVersionId().equals(currentVersionId),
                post.getTitle(),
                preview(post.getBody(), BODY_PREVIEW_LENGTH),
                post.getAuthorUserId(),
                authorNames.getOrDefault(post.getAuthorUserId(), "已注销用户"),
                post.getStatus().name(),
                comments.countByPostIdAndStatus(post.getId(), DiscussionPost.Status.VISIBLE),
                post.getCreatedAt());
    }

    private CommunityDtos.CommentView toCommentView(
            final DiscussionComment comment,
            final Map<UUID, DiscussionComment> commentMap,
            final Map<UUID, String> authorNames) {
        DiscussionComment quoted = comment.getQuotedCommentId() == null
                ? null
                : commentMap.get(comment.getQuotedCommentId());
        return new CommunityDtos.CommentView(
                comment.getId(),
                comment.getParentCommentId(),
                comment.getQuotedCommentId(),
                quoted == null ? null : preview(quoted.getBody(), BODY_PREVIEW_LENGTH),
                comment.getAuthorUserId(),
                authorNames.getOrDefault(comment.getAuthorUserId(), "已注销用户"),
                comment.getBody(),
                comment.getStatus().name(),
                comment.getCreatedAt());
    }

    private Map<UUID, String> authorNames(final Collection<UUID> authorIds) {
        return users.findAllById(authorIds).stream()
                .collect(Collectors.toMap(AppUser::getId, this::displayName));
    }

    private String displayName(final AppUser user) {
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        return user.getUsername();
    }

    private String preview(final String text, final int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "…";
    }
}
