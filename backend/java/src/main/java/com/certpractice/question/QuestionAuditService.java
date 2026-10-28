package com.certpractice.question;

import java.util.Map;
import java.util.UUID;

import com.certpractice.identity.AdminAuditLog;
import com.certpractice.identity.AdminAuditLogRepository;
import com.certpractice.shared.observability.TraceContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only audit records for question authoring, review and import actions.
 * Summaries must never contain passwords, secrets or answer material.
 */
@Service
public class QuestionAuditService {

    private final AdminAuditLogRepository auditLogs;
    private final ObjectMapper objectMapper;

    public QuestionAuditService(
            final AdminAuditLogRepository auditLogs,
            final ObjectMapper objectMapper) {
        this.auditLogs = auditLogs;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(
            final UUID actorUserId,
            final String action,
            final String targetType,
            final UUID targetId,
            final AdminAuditLog.Outcome outcome,
            final Map<String, Object> summary) {
        AdminAuditLog log = new AdminAuditLog();
        log.setActorUserId(actorUserId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setOutcome(outcome);
        log.setSummaryJson(toJson(summary));
        log.setTraceId(TraceContext.currentTraceId());
        auditLogs.save(log);
    }

    private String toJson(final Map<String, Object> summary) {
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }
}
