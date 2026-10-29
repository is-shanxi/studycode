package com.certpractice.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Administrator-facing payloads. Passwords and password hashes are never part
 * of these views; a generated temporary password is returned exactly once.
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record AdminUserView(
            UUID userId,
            String username,
            String nickname,
            String status,
            List<String> roles,
            Instant createdAt) {
    }

    public record ResetPasswordResponse(
            UUID userId,
            String username,
            String temporaryPassword) {
    }

    public record DashboardView(
            long activeUsers,
            long activeUsersLast7Days,
            long totalPracticeSessions,
            long completedPracticeSessions,
            double practiceCompletionRate,
            long objectiveGradedItems,
            long objectiveFailedItems,
            double questionErrorRate,
            long fillAnswers,
            long autoMatchedAnswers,
            double autoMatchRate,
            long selfAssessedItems,
            long selfPassedItems,
            long selfFailedItems,
            double selfAssessmentPassRate,
            long pendingReviewVersions,
            long openReports,
            long aiRequestCount,
            BigDecimal aiCost) {
    }

    public record AuditLogView(
            UUID auditLogId,
            UUID actorUserId,
            String action,
            String targetType,
            UUID targetId,
            String outcome,
            String summary,
            String traceId,
            Instant createdAt) {
    }
}
