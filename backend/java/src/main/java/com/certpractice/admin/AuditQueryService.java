package com.certpractice.admin;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.certpractice.identity.AdminAuditLog;
import com.certpractice.identity.AdminAuditLogRepository;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.web.PageQuery;
import com.certpractice.shared.web.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only access to append-only administrator audit records.
 */
@Service
public class AuditQueryService {

    private final AdminAuditLogRepository auditLogs;

    public AuditQueryService(final AdminAuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminDtos.AuditLogView> search(
            final UUID actorUserId,
            final String action,
            final String targetType,
            final String outcome,
            final PageQuery pageQuery) {
        Page<AdminAuditLog> page = auditLogs.search(
                actorUserId,
                normalize(action),
                normalize(targetType),
                parseOutcome(outcome),
                pageQuery.toPageable());
        List<AdminDtos.AuditLogView> items = page.getContent().stream()
                .map(log -> new AdminDtos.AuditLogView(
                        log.getId(),
                        log.getActorUserId(),
                        log.getAction(),
                        log.getTargetType(),
                        log.getTargetId(),
                        log.getOutcome().name(),
                        log.getSummaryJson(),
                        log.getTraceId(),
                        log.getCreatedAt()))
                .toList();
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    private String normalize(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AdminAuditLog.Outcome parseOutcome(final String outcome) {
        if (outcome == null || outcome.isBlank()) {
            return null;
        }
        try {
            return AdminAuditLog.Outcome.valueOf(outcome.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unsupported audit outcome");
        }
    }
}
