package com.certpractice.community;

import java.util.UUID;

import com.certpractice.shared.security.CurrentUserService;
import com.certpractice.shared.web.PageQuery;
import com.certpractice.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public discussion endpoints. All reads are scoped to the authenticated user
 * so in-progress practice sessions can hide question discussions.
 */
@RestController
@RequestMapping("/api/v1")
public class DiscussionController {

    private final DiscussionService discussionService;
    private final CommentService commentService;
    private final CurrentUserService currentUserService;

    public DiscussionController(
            final DiscussionService discussionService,
            final CommentService commentService,
            final CurrentUserService currentUserService) {
        this.discussionService = discussionService;
        this.commentService = commentService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/questions/{questionId}/discussions")
    public PageResponse<CommunityDtos.PostSummary> list(
            @PathVariable final UUID questionId,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size) {
        return discussionService.list(
                currentUserService.requireUserId(), questionId, new PageQuery(page, size));
    }

    @PostMapping("/questions/{questionId}/discussions")
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityDtos.PostSummary create(
            @PathVariable final UUID questionId,
            @Valid @RequestBody final CommunityDtos.CreatePostRequest request) {
        return discussionService.create(currentUserService.requireUserId(), questionId, request);
    }

    @GetMapping("/discussions/{postId}")
    public CommunityDtos.PostDetail detail(@PathVariable final UUID postId) {
        return discussionService.detail(currentUserService.requireUserId(), postId);
    }

    @PostMapping("/discussions/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityDtos.CommentView comment(
            @PathVariable final UUID postId,
            @Valid @RequestBody final CommunityDtos.CreateCommentRequest request) {
        return commentService.create(currentUserService.requireUserId(), postId, request);
    }
}
