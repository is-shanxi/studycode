package com.certpractice.admin;

import java.util.UUID;

import com.certpractice.community.CommunityDtos;
import com.certpractice.community.ModerationService;
import com.certpractice.shared.security.CurrentUserService;
import com.certpractice.shared.web.PageQuery;
import com.certpractice.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrator moderation queue for content reports and question corrections.
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
public class AdminModerationController {

    private final ModerationService moderationService;
    private final CurrentUserService currentUserService;

    public AdminModerationController(
            final ModerationService moderationService,
            final CurrentUserService currentUserService) {
        this.moderationService = moderationService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public PageResponse<CommunityDtos.ReportView> list(
            @RequestParam(required = false) final String source,
            @RequestParam(required = false) final String status,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size) {
        currentUserService.requireAdmin();
        return moderationService.listReports(source, status, new PageQuery(page, size));
    }

    @PostMapping("/{reportId}/resolve")
    public CommunityDtos.ResolveReportResponse resolve(
            @PathVariable final UUID reportId,
            @Valid @RequestBody final CommunityDtos.ResolveReportRequest request) {
        currentUserService.requireAdmin();
        return moderationService.resolve(
                currentUserService.requireUserId(), reportId, request);
    }
}
