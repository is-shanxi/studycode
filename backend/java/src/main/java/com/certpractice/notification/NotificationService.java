package com.certpractice.notification;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.certpractice.community.ContentFilter;
import com.certpractice.identity.AdminAuditLog;
import com.certpractice.identity.AdminAuditService;
import com.certpractice.identity.AppUser;
import com.certpractice.identity.AppUserRepository;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.web.PageQuery;
import com.certpractice.shared.web.PageResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistent in-app notifications for replies, moderation outcomes and system
 * announcements. Notification bodies never include credentials or answer data.
 */
@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final AppUserRepository users;
    private final AdminAuditService auditService;
    private final ContentFilter contentFilter;
    private final ObjectMapper objectMapper;

    public NotificationService(
            final NotificationRepository notifications,
            final AppUserRepository users,
            final AdminAuditService auditService,
            final ContentFilter contentFilter,
            final ObjectMapper objectMapper) {
        this.notifications = notifications;
        this.users = users;
        this.auditService = auditService;
        this.contentFilter = contentFilter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void notifyCommentReply(
            final UUID recipientUserId,
            final UUID actorUserId,
            final String postTitle,
            final UUID postId,
            final UUID commentId) {
        if (recipientUserId == null || recipientUserId.equals(actorUserId)) {
            return;
        }
        AppUser recipient = users.findById(recipientUserId).orElse(null);
        if (recipient == null || recipient.getStatus() != AppUser.Status.ACTIVE) {
            return;
        }
        String actorName = users.findById(actorUserId)
                .map(this::displayName)
                .orElse("其他用户");
        Notification notification = new Notification();
        notification.setRecipientUserId(recipientUserId);
        notification.setType(Notification.Type.COMMENT_REPLY);
        notification.setTitle("你的帖子有新回复");
        notification.setBody(actorName + " 回复了《" + postTitle + "》");
        notification.setPayloadJson(toJson(Map.of(
                "postId", postId.toString(),
                "commentId", commentId.toString())));
        notifications.save(notification);
    }

    @Transactional
    public void notifyModerationResult(
            final UUID recipientUserId,
            final String source,
            final UUID reportId,
            final String resolution) {
        AppUser recipient = users.findById(recipientUserId).orElse(null);
        if (recipient == null || recipient.getStatus() != AppUser.Status.ACTIVE) {
            return;
        }
        String sourceLabel = "CORRECTION".equalsIgnoreCase(source) ? "题目纠错" : "内容举报";
        String outcome = "RESOLVED".equalsIgnoreCase(resolution) ? "已处理" : "未采纳";
        Notification notification = new Notification();
        notification.setRecipientUserId(recipientUserId);
        notification.setType(Notification.Type.MODERATION_RESULT);
        notification.setTitle("举报处理结果");
        notification.setBody("你的" + sourceLabel + outcome + "。");
        notification.setPayloadJson(toJson(Map.of(
                "reportId", reportId.toString(),
                "source", source,
                "status", resolution)));
        notifications.save(notification);
    }

    @Transactional
    public NotificationDtos.AnnouncementResponse announce(
            final UUID adminUserId,
            final NotificationDtos.AnnouncementRequest request) {
        String title = contentFilter.sanitizeRequired(request.title(), "title");
        String body = contentFilter.sanitizeRequired(request.body(), "body");
        List<AppUser> recipients = users.findByStatus(AppUser.Status.ACTIVE);
        for (AppUser recipient : recipients) {
            Notification notification = new Notification();
            notification.setRecipientUserId(recipient.getId());
            notification.setType(Notification.Type.ANNOUNCEMENT);
            notification.setTitle(title);
            notification.setBody(body);
            notifications.save(notification);
        }
        auditService.record(
                adminUserId,
                "ANNOUNCEMENT_PUBLISH",
                "NOTIFICATION",
                null,
                AdminAuditLog.Outcome.SUCCESS,
                Map.of("recipientCount", recipients.size()));
        return new NotificationDtos.AnnouncementResponse(recipients.size());
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDtos.NotificationView> list(
            final UUID userId,
            final boolean unreadOnly,
            final PageQuery pageQuery) {
        Page<Notification> page = unreadOnly
                ? notifications.findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(
                        userId, pageQuery.toPageable())
                : notifications.findByRecipientUserIdOrderByCreatedAtDesc(userId, pageQuery.toPageable());
        List<NotificationDtos.NotificationView> items = page.getContent().stream()
                .map(this::toView)
                .toList();
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Transactional
    public void markRead(final UUID userId, final UUID notificationId) {
        Notification notification = notifications.findById(notificationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Notification not found"));
        if (!notification.getRecipientUserId().equals(userId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Notification not found");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notifications.save(notification);
        }
    }

    @Transactional
    public NotificationDtos.MarkAllReadResponse markAllRead(final UUID userId) {
        int updated = notifications.markAllRead(userId, Instant.now());
        return new NotificationDtos.MarkAllReadResponse(updated);
    }

    private NotificationDtos.NotificationView toView(final Notification notification) {
        return new NotificationDtos.NotificationView(
                notification.getId(),
                notification.getType().name(),
                notification.getTitle(),
                notification.getBody(),
                notification.getPayloadJson(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }

    private String displayName(final AppUser user) {
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        return user.getUsername();
    }

    private String toJson(final Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }
}
