package com.certpractice.identity;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {

    Page<AdminAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
