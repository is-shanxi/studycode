package com.certpractice.shared.error;

import java.util.Locale;

import org.springframework.http.HttpStatus;

/**
 * Stable public error codes. The enum name is the API contract; the HTTP status
 * and title are used to build RFC 9457 Problem Details responses.
 */
public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Unauthenticated"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),
    ADMIN_CANNOT_DELETE_SELF(HttpStatus.FORBIDDEN, "Administrator cannot delete self"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
    STATE_CONFLICT(HttpStatus.CONFLICT, "State conflict"),
    IDEMPOTENCY_MISMATCH(HttpStatus.CONFLICT, "Idempotency mismatch"),
    ANSWERS_INCOMPLETE(HttpStatus.UNPROCESSABLE_ENTITY, "Answers are incomplete"),
    NO_ELIGIBLE_QUESTIONS(HttpStatus.UNPROCESSABLE_ENTITY, "No eligible questions"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Rate limited"),
    AI_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AI quota exceeded"),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI unavailable"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(final HttpStatus status, final String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String typeUri() {
        String slug = name().toLowerCase(Locale.ROOT).replace('_', '-');
        return "https://local.cert-practice/errors/" + slug;
    }
}
