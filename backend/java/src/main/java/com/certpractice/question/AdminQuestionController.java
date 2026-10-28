package com.certpractice.question;

import java.util.UUID;

import com.certpractice.shared.security.CurrentUserService;
import com.certpractice.shared.web.PageQuery;
import com.certpractice.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin question authoring and lifecycle endpoints. The /admin path is guarded
 * by role at the security layer; the service still derives the actor from the
 * server-side session for audit records.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminQuestionController {

    private final QuestionAuthoringService authoringService;
    private final QuestionLifecycleService lifecycleService;
    private final QuestionSearchService searchService;
    private final CurrentUserService currentUserService;

    public AdminQuestionController(
            final QuestionAuthoringService authoringService,
            final QuestionLifecycleService lifecycleService,
            final QuestionSearchService searchService,
            final CurrentUserService currentUserService) {
        this.authoringService = authoringService;
        this.lifecycleService = lifecycleService;
        this.searchService = searchService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/questions")
    public PageResponse<QuestionDtos.AdminQuestionSummary> search(
            @RequestParam(required = false) final String keyword,
            @RequestParam(required = false) final String status,
            @RequestParam(required = false) final String type,
            @RequestParam(required = false) final UUID subjectId,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size) {
        return searchService.searchAdmin(keyword, status, type, subjectId, new PageQuery(page, size));
    }

    @PostMapping("/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionDtos.AdminQuestionDetail create(
            @Valid @RequestBody final QuestionDtos.CreateQuestionRequest request) {
        return authoringService.createQuestion(currentUserService.requireUserId(), request);
    }

    @GetMapping("/questions/{questionId}")
    public QuestionDtos.AdminQuestionDetail detail(@PathVariable final UUID questionId) {
        return searchService.getAdminQuestion(questionId);
    }

    @PostMapping("/questions/{questionId}/versions")
    public QuestionDtos.AdminQuestionDetail cloneVersion(@PathVariable final UUID questionId) {
        return authoringService.cloneVersion(currentUserService.requireUserId(), questionId);
    }

    @PutMapping("/question-versions/{versionId}")
    public QuestionDtos.AdminQuestionDetail updateDraft(
            @PathVariable final UUID versionId,
            @Valid @RequestBody final QuestionDtos.UpdateDraftRequest request) {
        return authoringService.updateDraft(currentUserService.requireUserId(), versionId, request);
    }

    @DeleteMapping("/question-versions/{versionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(@PathVariable final UUID versionId) {
        authoringService.deleteDraft(currentUserService.requireUserId(), versionId);
    }

    @PostMapping("/question-versions/{versionId}/submit-review")
    public QuestionDtos.QuestionStatusResponse submitReview(
            @PathVariable final UUID versionId,
            @RequestBody(required = false) final QuestionDtos.ReviewRequest request) {
        return lifecycleService.submitReview(currentUserService.requireUserId(), versionId, request);
    }

    @PostMapping("/question-versions/{versionId}/approve")
    public QuestionDtos.QuestionStatusResponse approve(
            @PathVariable final UUID versionId,
            @RequestBody(required = false) final QuestionDtos.ReviewRequest request) {
        return lifecycleService.approve(currentUserService.requireUserId(), versionId, request);
    }

    @PostMapping("/question-versions/{versionId}/reject")
    public QuestionDtos.QuestionStatusResponse reject(
            @PathVariable final UUID versionId,
            @RequestBody(required = false) final QuestionDtos.ReviewRequest request) {
        return lifecycleService.reject(currentUserService.requireUserId(), versionId, request);
    }

    @PostMapping("/question-versions/{versionId}/publish")
    public QuestionDtos.QuestionStatusResponse publish(
            @PathVariable final UUID versionId,
            @RequestBody(required = false) final QuestionDtos.ReviewRequest request) {
        return lifecycleService.publish(currentUserService.requireUserId(), versionId, request);
    }

    @PostMapping("/questions/{questionId}/offline")
    public QuestionDtos.QuestionStatusResponse offline(
            @PathVariable final UUID questionId,
            @RequestBody(required = false) final QuestionDtos.ReviewRequest request) {
        return lifecycleService.offline(currentUserService.requireUserId(), questionId, request);
    }
}
