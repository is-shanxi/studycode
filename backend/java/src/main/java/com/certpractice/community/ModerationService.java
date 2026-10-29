package com.certpractice.community;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.certpractice.identity.AdminAuditLog;
import com.certpractice.identity.AdminAuditService;
import com.certpractice.identity.AppUser;
import com.certpractice.identity.AppUserRepository;
import com.certpractice.notification.NotificationService;
import com.certpractice.question.CorrectionReport;
import com.certpractice.question.CorrectionReportRepository;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.web.PageQuery;
import com.certpractice.shared.web.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User reports, correction reports and administrator moderation. Resolution is
 * audited and notifies the reporter; hidden or removed content immediately
 * disappears from public discussion views.
 */
@Service
public class ModerationService {

    private static final Set<String> REPORT_STATUSES =
            Set.of("OPEN", "PROCESSING", "RESOLVED", "REJECTED");
    private static final Set<String> RESOLUTIONS = Set.of("PROCESSING", "RESOLVED", "REJECTED");
    private static final Set<String> MODERATION_ACTIONS = Set.of("HIDE", "REMOVE");

    private final ContentReportRepository contentReports;
    private final CorrectionReportRepository correctionReports;
    private final DiscussionPostRepository posts;
    private final DiscussionCommentRepository comments;
    private final DiscussionAccessService accessService;
    private final NotificationService notificationService;
    private final AdminAuditService auditService;
    private final AppUserRepository users;
    private final ContentFilter contentFilter;

    public ModerationService(
            final ContentReportRepository contentReports,
            final CorrectionReportRepository correctionReports,
            final DiscussionPostRepository posts,
            final DiscussionCommentRepository comments,
            final DiscussionAccessService accessService,
            final NotificationService notificationService,
            final AdminAuditService auditService,
            final AppUserRepository users,
            final ContentFilter contentFilter) {
        this.contentReports = contentReports;
        this.correctionReports = correctionReports;
        this.posts = posts;
        this.comments = comments;
        this.accessService = accessService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.users = users;
        this.contentFilter = contentFilter;
    }

    @Transactional
    public CommunityDtos.ReportCreationResult reportPost(
            final UUID userId,
            final UUID postId,
            final CommunityDtos.ReportRequest request) {
        DiscussionPost post = accessService.requireVisiblePost(postId);
        accessService.requireDiscussionAccess(userId, post.getQuestionId());
        return createContentReport(
                userId, ContentReport.TargetType.POST, postId, request.reason());
    }

    @Transactional
    public CommunityDtos.ReportCreationResult reportComment(
            final UUID userId,
            final UUID commentId,
            final CommunityDtos.ReportRequest request) {
        DiscussionComment comment = comments.findById(commentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Comment not found"));
        if (comment.getStatus() != DiscussionPost.Status.VISIBLE) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Comment not found");
        }
        DiscussionPost post = accessService.requireVisiblePost(comment.getPostId());
        accessService.requireDiscussionAccess(userId, post.getQuestionId());
        return createContentReport(
                userId, ContentReport.TargetType.COMMENT, commentId, request.reason());
    }

    @Transactional(readOnly = true)
    public PageResponse<CommunityDtos.ReportView> listReports(
            final String source,
            final String status,
            final PageQuery pageQuery) {
        String normalizedSource = normalizeSource(source, true);
        String normalizedStatus = normalizeStatus(status);

        List<CommunityDtos.ReportView> views = new ArrayList<>();
        if (normalizedSource == null || "CONTENT".equals(normalizedSource)) {
            contentReports.findByStatusInOrderByCreatedAtAsc(contentStatuses(normalizedStatus))
                    .forEach(report -> views.add(toReportView(report)));
        }
        if (normalizedSource == null || "CORRECTION".equals(normalizedSource)) {
            correctionReports.findByStatusInOrderByCreatedAtAsc(correctionStatuses(normalizedStatus))
                    .forEach(report -> views.add(toReportView(report)));
        }

        List<UUID> reporterIds = views.stream()
                .map(CommunityDtos.ReportView::reporterUserId)
                .distinct()
                .toList();
        Map<UUID, String> reporterNames = users.findAllById(reporterIds).stream()
                .collect(Collectors.toMap(AppUser::getId, this::displayName));
        List<CommunityDtos.ReportView> namedViews = views.stream()
                .map(view -> new CommunityDtos.ReportView(
                        view.reportId(),
                        view.source(),
                        view.targetType(),
                        view.targetId(),
                        view.questionVersionId(),
                        view.reason(),
                        view.status(),
                        view.reporterUserId(),
                        reporterNames.getOrDefault(view.reporterUserId(), "已注销用户"),
                        view.handledBy(),
                        view.handledAt(),
                        view.createdAt()))
                .sorted(Comparator.comparing(
                        CommunityDtos.ReportView::createdAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int total = namedViews.size();
        int from = Math.min(pageQuery.page() * pageQuery.size(), total);
        int to = Math.min(from + pageQuery.size(), total);
        return new PageResponse<>(
                List.copyOf(namedViews.subList(from, to)),
                pageQuery.page(),
                pageQuery.size(),
                total);
    }

    @Transactional
    public CommunityDtos.ResolveReportResponse resolve(
            final UUID adminUserId,
            final UUID reportId,
            final CommunityDtos.ResolveReportRequest request) {
        String source = normalizeSource(request.source(), false);
        String resolution = normalizeResolution(request.resolution());
        String action = normalizeModerationAction(request.moderationAction());
        String note = request.note() == null ? null : contentFilter.sanitize(request.note());

        if ("CONTENT".equals(source)) {
            return resolveContentReport(adminUserId, reportId, resolution, action, note);
        }
        return resolveCorrectionReport(adminUserId, reportId, resolution, note);
    }

    private CommunityDtos.ReportCreationResult createContentReport(
            final UUID userId,
            final ContentReport.TargetType targetType,
            final UUID targetId,
            final String reason) {
        String sanitizedReason = contentFilter.sanitizeRequired(reason, "reason");
        ContentReport existing = contentReports
                .findFirstByReporterUserIdAndTargetTypeAndTargetIdAndStatusInOrderByCreatedAtAsc(
                        userId,
                        targetType,
                        targetId,
                        List.of(ContentReport.Status.OPEN, ContentReport.Status.PROCESSING))
                .orElse(null);
        if (existing != null) {
            return new CommunityDtos.ReportCreationResult(
                    new CommunityDtos.ReportCreated(existing.getId(), existing.getStatus().name()),
                    false);
        }

        ContentReport report = new ContentReport();
        report.setReporterUserId(userId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReason(sanitizedReason);
        report.setStatus(ContentReport.Status.OPEN);
        contentReports.save(report);
        return new CommunityDtos.ReportCreationResult(
                new CommunityDtos.ReportCreated(report.getId(), report.getStatus().name()),
                true);
    }

    private CommunityDtos.ResolveReportResponse resolveContentReport(
            final UUID adminUserId,
            final UUID reportId,
            final String resolution,
            final String action,
            final String note) {
        ContentReport report = contentReports.findById(reportId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Report not found"));
        guardNotTerminal(report.getStatus() == ContentReport.Status.RESOLVED
                || report.getStatus() == ContentReport.Status.REJECTED);

        String targetStatus = null;
        if ("PROCESSING".equals(resolution)) {
            report.setStatus(ContentReport.Status.PROCESSING);
        } else {
            report.setStatus(ContentReport.Status.valueOf(resolution));
            if ("RESOLVED".equals(resolution) && action != null) {
                targetStatus = applyModerationAction(report, action);
            }
        }
        report.setHandledBy(adminUserId);
        report.setHandledAt(Instant.now());
        contentReports.save(report);
        recordAudit(adminUserId, "CONTENT_REPORT_RESOLVE", reportId, resolution, action, note);
        if (!"PROCESSING".equals(resolution)) {
            notificationService.notifyModerationResult(
                    report.getReporterUserId(), "CONTENT", reportId, resolution);
        }
        return new CommunityDtos.ResolveReportResponse(
                reportId, "CONTENT", report.getStatus().name(), targetStatus);
    }

    private CommunityDtos.ResolveReportResponse resolveCorrectionReport(
            final UUID adminUserId,
            final UUID reportId,
            final String resolution,
            final String note) {
        CorrectionReport report = correctionReports.findById(reportId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Report not found"));
        guardNotTerminal(report.getStatus() == CorrectionReport.Status.RESOLVED
                || report.getStatus() == CorrectionReport.Status.REJECTED);

        report.setStatus(CorrectionReport.Status.valueOf(resolution));
        report.setHandledBy(adminUserId);
        report.setHandledAt(Instant.now());
        correctionReports.save(report);
        recordAudit(adminUserId, "CORRECTION_REPORT_RESOLVE", reportId, resolution, null, note);
        if (!"PROCESSING".equals(resolution)) {
            notificationService.notifyModerationResult(
                    report.getReporterUserId(), "CORRECTION", reportId, resolution);
        }
        return new CommunityDtos.ResolveReportResponse(
                reportId, "CORRECTION", report.getStatus().name(), null);
    }

    private String applyModerationAction(final ContentReport report, final String action) {
        if (report.getTargetType() == ContentReport.TargetType.QUESTION) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "Question reports must be handled through correction reports");
        }
        if (report.getTargetType() == ContentReport.TargetType.POST) {
            DiscussionPost post = posts.findById(report.getTargetId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Reported post not found"));
            post.setStatus("HIDE".equals(action)
                    ? DiscussionPost.Status.HIDDEN
                    : DiscussionPost.Status.REMOVED);
            posts.save(post);
            return post.getStatus().name();
        }
        DiscussionComment comment = comments.findById(report.getTargetId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Reported comment not found"));
        comment.setStatus("HIDE".equals(action)
                ? DiscussionPost.Status.HIDDEN
                : DiscussionPost.Status.REMOVED);
        comments.save(comment);
        return comment.getStatus().name();
    }

    private void recordAudit(
            final UUID adminUserId,
            final String action,
            final UUID reportId,
            final String resolution,
            final String moderationAction,
            final String note) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("resolution", resolution);
        summary.put("moderationAction", moderationAction == null ? "NONE" : moderationAction);
        if (note != null && !note.isBlank()) {
            summary.put("note", note);
        }
        auditService.record(
                adminUserId,
                action,
                action.startsWith("CORRECTION") ? "CORRECTION_REPORT" : "CONTENT_REPORT",
                reportId,
                AdminAuditLog.Outcome.SUCCESS,
                summary);
    }

    private CommunityDtos.ReportView toReportView(final ContentReport report) {
        return new CommunityDtos.ReportView(
                report.getId(),
                "CONTENT",
                report.getTargetType().name(),
                report.getTargetId(),
                null,
                report.getReason(),
                report.getStatus().name(),
                report.getReporterUserId(),
                null,
                report.getHandledBy(),
                report.getHandledAt(),
                report.getCreatedAt());
    }

    private CommunityDtos.ReportView toReportView(final CorrectionReport report) {
        return new CommunityDtos.ReportView(
                report.getId(),
                "CORRECTION",
                report.getTarget().name(),
                report.getQuestionId(),
                report.getQuestionVersionId(),
                report.getDescription(),
                report.getStatus().name(),
                report.getReporterUserId(),
                null,
                report.getHandledBy(),
                report.getHandledAt(),
                report.getCreatedAt());
    }

    private Collection<ContentReport.Status> contentStatuses(final String status) {
        if (status == null) {
            return List.of(ContentReport.Status.values());
        }
        return List.of(ContentReport.Status.valueOf(status));
    }

    private Collection<CorrectionReport.Status> correctionStatuses(final String status) {
        if (status == null) {
            return List.of(CorrectionReport.Status.values());
        }
        return List.of(CorrectionReport.Status.valueOf(status));
    }

    private String normalizeSource(final String source, final boolean nullable) {
        if (source == null || source.isBlank()) {
            if (nullable) {
                return null;
            }
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "source is required");
        }
        String normalized = source.trim().toUpperCase(Locale.ROOT);
        if (!"CONTENT".equals(normalized) && !"CORRECTION".equals(normalized)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "source must be CONTENT or CORRECTION");
        }
        return normalized;
    }

    private String normalizeStatus(final String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!REPORT_STATUSES.contains(normalized)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unsupported report status");
        }
        return normalized;
    }

    private String normalizeResolution(final String resolution) {
        if (resolution == null || resolution.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "resolution is required");
        }
        String normalized = resolution.trim().toUpperCase(Locale.ROOT);
        if (!RESOLUTIONS.contains(normalized)) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "resolution must be PROCESSING, RESOLVED or REJECTED");
        }
        return normalized;
    }

    private String normalizeModerationAction(final String action) {
        if (action == null || action.isBlank()) {
            return null;
        }
        String normalized = action.trim().toUpperCase(Locale.ROOT);
        if (!MODERATION_ACTIONS.contains(normalized)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "moderationAction must be HIDE or REMOVE");
        }
        return normalized;
    }

    private void guardNotTerminal(final boolean terminal) {
        if (terminal) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "Report has already been resolved");
        }
    }

    private String displayName(final AppUser user) {
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        return user.getUsername();
    }
}
