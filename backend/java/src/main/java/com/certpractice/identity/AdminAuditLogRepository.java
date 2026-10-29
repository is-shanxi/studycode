package com.certpractice.identity;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {

    Page<AdminAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
            select log from AdminAuditLog log
            where (:actorUserId is null or log.actorUserId = :actorUserId)
              and (:action is null or log.action = :action)
              and (:targetType is null or log.targetType = :targetType)
              and (:outcome is null or log.outcome = :outcome)
            order by log.createdAt desc
            """)
    Page<AdminAuditLog> search(
            @Param("actorUserId") UUID actorUserId,
            @Param("action") String action,
            @Param("targetType") String targetType,
            @Param("outcome") AdminAuditLog.Outcome outcome,
            Pageable pageable);
}
