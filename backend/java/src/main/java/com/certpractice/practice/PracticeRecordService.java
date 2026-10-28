package com.certpractice.practice;

import java.util.List;
import java.util.UUID;

import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.web.Guard;
import com.certpractice.shared.web.PageQuery;
import com.certpractice.shared.web.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * History queries always resolve the exact version frozen into the session, so
 * later question edits never rewrite a historical result.
 */
@Service
public class PracticeRecordService {

    private final PracticeSessionRepository sessions;
    private final PracticeDtoMapper mapper;

    public PracticeRecordService(
            final PracticeSessionRepository sessions,
            final PracticeDtoMapper mapper) {
        this.sessions = sessions;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<PracticeDtos.RecordSummary> list(
            final UUID userId,
            final String mode,
            final String status,
            final PageQuery pageQuery) {
        PracticeSession.Mode parsedMode = parseMode(mode);
        PracticeSession.Status parsedStatus = parseStatus(status);
        Page<PracticeSession> page = sessions.search(
                userId, parsedMode, parsedStatus, pageQuery.toPageable());
        List<PracticeDtos.RecordSummary> items = page.getContent().stream()
                .map(session -> mapper.toRecordSummary(session, mapper.items(session.getId())))
                .toList();
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public PracticeDtos.ResultView detail(final UUID userId, final UUID sessionId) {
        PracticeSession session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Record not found"));
        Guard.require(session.getStatus() != PracticeSession.Status.IN_PROGRESS
                        && session.getStatus() != PracticeSession.Status.ABANDONED,
                ErrorCode.STATE_CONFLICT, "Record is not available before submission");
        return mapper.toResultView(session, mapper.items(sessionId));
    }

    private PracticeSession.Mode parseMode(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PracticeSession.Mode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid mode", exception);
        }
    }

    private PracticeSession.Status parseStatus(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PracticeSession.Status.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid status", exception);
        }
    }
}
