package com.certpractice.community;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Community payloads. Public views expose author display names only and never
 * include private account fields.
 */
public final class CommunityDtos {

    private CommunityDtos() {
    }

    public record CreatePostRequest(
            @NotNull UUID questionVersionId,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 10_000) String body) {
    }

    public record CreateCommentRequest(
            UUID parentCommentId,
            UUID quotedCommentId,
            @NotBlank @Size(max = 5_000) String body) {
    }

    public record ReportRequest(@NotBlank @Size(max = 2_000) String reason) {
    }

    public record CorrectionReportRequest(
            @NotNull UUID questionId,
            @NotNull UUID questionVersionId,
            @NotBlank String target,
            @NotBlank @Size(max = 5_000) String description) {
    }

    public record PostSummary(
            UUID postId,
            UUID questionId,
            UUID questionVersionId,
            int versionNo,
            boolean versionCurrent,
            String title,
            String bodyPreview,
            UUID authorUserId,
            String authorName,
            String status,
            long commentCount,
            Instant createdAt) {
    }

    public record CommentView(
            UUID commentId,
            UUID parentCommentId,
            UUID quotedCommentId,
            String quotedPreview,
            UUID authorUserId,
            String authorName,
            String body,
            String status,
            Instant createdAt) {
    }

    public record PostDetail(
            UUID postId,
            UUID questionId,
            UUID questionVersionId,
            int versionNo,
            boolean versionCurrent,
            String title,
            String body,
            UUID authorUserId,
            String authorName,
            String status,
            Instant createdAt,
            List<CommentView> comments) {
    }

    public record ReportCreated(UUID reportId, String status) {
    }

    public record ReportCreationResult(ReportCreated report, boolean created) {
    }

    public record ReportView(
            UUID reportId,
            String source,
            String targetType,
            UUID targetId,
            UUID questionVersionId,
            String reason,
            String status,
            UUID reporterUserId,
            String reporterName,
            UUID handledBy,
            Instant handledAt,
            Instant createdAt) {
    }

    public record ResolveReportRequest(
            @NotBlank String source,
            @NotBlank String resolution,
            String moderationAction,
            String note) {
    }

    public record ResolveReportResponse(
            UUID reportId,
            String source,
            String status,
            String targetStatus) {
    }
}
