package com.certpractice.question;

import java.util.Map;
import java.util.UUID;

import com.certpractice.identity.AdminAuditLog;
import com.certpractice.identity.AdminAuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only audit records for question authoring, review and import actions.
 * Delegates persistence to the shared administrator audit service.
 */
@Service
public class QuestionAuditService {

    private final AdminAuditService auditService;

    public QuestionAuditService(final AdminAuditService auditService) {
        this.auditService = auditService;
    }

    @Transactional
    public void record(
            final UUID actorUserId,
            final String action,
            final String targetType,
            final UUID targetId,
            final AdminAuditLog.Outcome outcome,
            final Map<String, Object> summary) {
        auditService.record(actorUserId, action, targetType, targetId, outcome, summary);
    }
}
