package com.certpractice.community;

import java.util.UUID;

import com.certpractice.shared.security.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * User-initiated content reports and question correction reports. Duplicate
 * open reports by the same user return the existing record.
 */
@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final ModerationService moderationService;
    private final CorrectionReportService correctionReportService;
    private final CurrentUserService currentUserService;

    public ReportController(
            final ModerationService moderationService,
            final CorrectionReportService correctionReportService,
            final CurrentUserService currentUserService) {
        this.moderationService = moderationService;
        this.correctionReportService = correctionReportService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/discussions/{postId}/report")
    public ResponseEntity<CommunityDtos.ReportCreated> reportPost(
            @PathVariable final UUID postId,
            @Valid @RequestBody final CommunityDtos.ReportRequest request) {
        return toResponse(moderationService.reportPost(
                currentUserService.requireUserId(), postId, request));
    }

    @PostMapping("/comments/{commentId}/report")
    public ResponseEntity<CommunityDtos.ReportCreated> reportComment(
            @PathVariable final UUID commentId,
            @Valid @RequestBody final CommunityDtos.ReportRequest request) {
        return toResponse(moderationService.reportComment(
                currentUserService.requireUserId(), commentId, request));
    }

    @PostMapping("/correction-reports")
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityDtos.ReportCreated correctionReport(
            @Valid @RequestBody final CommunityDtos.CorrectionReportRequest request) {
        return correctionReportService.create(currentUserService.requireUserId(), request);
    }

    private ResponseEntity<CommunityDtos.ReportCreated> toResponse(
            final CommunityDtos.ReportCreationResult result) {
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.report());
    }
}
