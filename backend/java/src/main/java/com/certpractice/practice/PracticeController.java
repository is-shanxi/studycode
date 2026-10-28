package com.certpractice.practice;

import java.util.UUID;

import com.certpractice.shared.security.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Practice and self-test session endpoints shared by both modes.
 */
@RestController
@RequestMapping("/api/v1/practice-sessions")
public class PracticeController {

    private final PracticeSessionService sessionService;
    private final PracticeSubmissionService submissionService;
    private final FillAssessmentService assessmentService;
    private final PracticeRecordService recordService;
    private final CurrentUserService currentUserService;

    public PracticeController(
            final PracticeSessionService sessionService,
            final PracticeSubmissionService submissionService,
            final FillAssessmentService assessmentService,
            final PracticeRecordService recordService,
            final CurrentUserService currentUserService) {
        this.sessionService = sessionService;
        this.submissionService = submissionService;
        this.assessmentService = assessmentService;
        this.recordService = recordService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PracticeDtos.SessionView create(
            @Valid @RequestBody final PracticeDtos.CreateSessionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) final String idempotencyKey) {
        return sessionService.create(currentUserService.requireUserId(), request, idempotencyKey);
    }

    @GetMapping("/{sessionId}")
    public PracticeDtos.SessionView get(@PathVariable final UUID sessionId) {
        return sessionService.getSession(currentUserService.requireUserId(), sessionId);
    }

    @PutMapping("/{sessionId}/items/{itemId}/answer")
    public PracticeDtos.SaveAnswerResponse saveAnswer(
            @PathVariable final UUID sessionId,
            @PathVariable final UUID itemId,
            @Valid @RequestBody final PracticeDtos.SaveAnswerRequest request) {
        return sessionService.saveAnswer(
                currentUserService.requireUserId(), sessionId, itemId, request);
    }

    @PostMapping("/{sessionId}/submit")
    public PracticeDtos.ResultView submit(
            @PathVariable final UUID sessionId,
            @RequestHeader(value = "Idempotency-Key", required = false) final String idempotencyKey) {
        return submissionService.submit(currentUserService.requireUserId(), sessionId, idempotencyKey);
    }

    @PostMapping("/{sessionId}/items/{itemId}/self-assessment")
    public PracticeDtos.SelfAssessmentResponse selfAssessment(
            @PathVariable final UUID sessionId,
            @PathVariable final UUID itemId,
            @Valid @RequestBody final PracticeDtos.SelfAssessmentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) final String idempotencyKey) {
        return assessmentService.assess(
                currentUserService.requireUserId(), sessionId, itemId, request, idempotencyKey);
    }

    @PostMapping("/{sessionId}/abandon")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void abandon(@PathVariable final UUID sessionId) {
        sessionService.abandon(currentUserService.requireUserId(), sessionId);
    }

    @GetMapping("/{sessionId}/result")
    public PracticeDtos.ResultView result(@PathVariable final UUID sessionId) {
        return recordService.detail(currentUserService.requireUserId(), sessionId);
    }
}
