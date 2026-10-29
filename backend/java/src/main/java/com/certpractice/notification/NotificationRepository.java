package com.certpractice.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId, Pageable pageable);

    Page<Notification> findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(
            UUID recipientUserId,
            Pageable pageable);

    long countByRecipientUserIdAndReadAtIsNull(UUID recipientUserId);

    List<Notification> findByRecipientUserIdAndType(UUID recipientUserId, Notification.Type type);

    @Modifying
    @Query("""
            update Notification notification
            set notification.readAt = :readAt
            where notification.recipientUserId = :userId
              and notification.readAt is null
            """)
    int markAllRead(@Param("userId") UUID userId, @Param("readAt") Instant readAt);
}
