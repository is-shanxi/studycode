package com.certpractice.shared.error;

import java.util.List;

/**
 * RFC 9457 Problem Details payload used by all public error responses.
 */
public record ProblemDetails(
        String type,
        String title,
        int status,
        String detail,
        String code,
        String traceId,
        List<FieldErrorDetail> errors) {

    public static ProblemDetails from(
            final ErrorCode errorCode,
            final String detail,
            final String traceId,
            final List<FieldErrorDetail> errors) {
        String resolvedDetail = detail == null || detail.isBlank() ? errorCode.title() : detail;
        return new ProblemDetails(
                errorCode.typeUri(),
                errorCode.title(),
                errorCode.status().value(),
                resolvedDetail,
                errorCode.name(),
                traceId,
                errors);
    }

    public record FieldErrorDetail(String field, String message) {
    }
}
