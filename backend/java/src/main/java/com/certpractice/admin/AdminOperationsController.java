package com.certpractice.admin;

import java.util.UUID;

import com.certpractice.notification.NotificationDtos;
import com.certpractice.notification.NotificationService;
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
 * Administrator user management, dashboard, audit query and announcements. The
 * /admin path is guarded by role at the security layer and re-checked here.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminOperationsController {

    private final AdminUserService userService;
    private final DashboardService dashboardService;
    private final AuditQueryService auditQueryService;
    private final NotificationService notificationService;
    private final CurrentUserService currentUserService;

    public AdminOperationsController(
            final AdminUserService userService,
            final DashboardService dashboardService,
            final AuditQueryService auditQueryService,
            final NotificationService notificationService,
            final CurrentUserService currentUserService) {
        this.userService = userService;
        this.dashboardService = dashboardService;
        this.auditQueryService = auditQueryService;
        this.notificationService = notificationService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/users")
    public PageResponse<AdminDtos.AdminUserView> users(
            @RequestParam(required = false) final String keyword,
            @RequestParam(required = false) final String status,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size) {
        currentUserService.requireAdmin();
        return userService.search(keyword, status, new PageQuery(page, size));
    }

    @PostMapping("/users/{userId}/disable")
    public AdminDtos.AdminUserView disable(@PathVariable final UUID userId) {
        currentUserService.requireAdmin();
        return userService.disable(currentUserService.requireUserId(), userId);
    }

    @PostMapping("/users/{userId}/enable")
    public AdminDtos.AdminUserView enable(@PathVariable final UUID userId) {
        currentUserService.requireAdmin();
        return userService.enable(currentUserService.requireUserId(), userId);
    }

    @PostMapping("/users/{userId}/reset-password")
    public AdminDtos.ResetPasswordResponse resetPassword(@PathVariable final UUID userId) {
        currentUserService.requireAdmin();
        return userService.resetPassword(currentUserService.requireUserId(), userId);
    }

    @GetMapping("/dashboard")
    public AdminDtos.DashboardView dashboard() {
        currentUserService.requireAdmin();
        return dashboardService.dashboard();
    }

    @GetMapping("/audit-logs")
    public PageResponse<AdminDtos.AuditLogView> auditLogs(
            @RequestParam(required = false) final UUID actorUserId,
            @RequestParam(required = false) final String action,
            @RequestParam(required = false) final String targetType,
            @RequestParam(required = false) final String outcome,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size) {
        currentUserService.requireAdmin();
        return auditQueryService.search(
                actorUserId, action, targetType, outcome, new PageQuery(page, size));
    }

    @PostMapping("/notifications/announcements")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationDtos.AnnouncementResponse announce(
            @Valid @RequestBody final NotificationDtos.AnnouncementRequest request) {
        currentUserService.requireAdmin();
        return notificationService.announce(currentUserService.requireUserId(), request);
    }
}
