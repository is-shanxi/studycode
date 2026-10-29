package com.certpractice.notification;

import java.util.UUID;

import com.certpractice.shared.security.CurrentUserService;
import com.certpractice.shared.web.PageQuery;
import com.certpractice.shared.web.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Current-user notification center. Ownership is derived from the server-side
 * session and never from a client-supplied user id.
 */
@RestController
@RequestMapping("/api/v1/me/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserService currentUserService;

    public NotificationController(
            final NotificationService notificationService,
            final CurrentUserService currentUserService) {
        this.notificationService = notificationService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public PageResponse<NotificationDtos.NotificationView> list(
            @RequestParam(defaultValue = "false") final boolean unreadOnly,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size) {
        return notificationService.list(
                currentUserService.requireUserId(), unreadOnly, new PageQuery(page, size));
    }

    @PutMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable final UUID notificationId) {
        notificationService.markRead(currentUserService.requireUserId(), notificationId);
    }

    @PutMapping("/read-all")
    public NotificationDtos.MarkAllReadResponse markAllRead() {
        return notificationService.markAllRead(currentUserService.requireUserId());
    }
}
