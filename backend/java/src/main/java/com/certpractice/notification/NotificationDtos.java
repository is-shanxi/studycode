package com.certpractice.notification;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Notification payloads. Responses are scoped to the authenticated recipient.
 */
public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationView(
            UUID notificationId,
            String type,
            String title,
            String body,
            String payload,
            Instant readAt,
            Instant createdAt) {
    }

    public record MarkAllReadResponse(int updated) {
    }

    public record AnnouncementRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 5_000) String body) {
    }

    public record AnnouncementResponse(int recipientCount) {
    }
}
