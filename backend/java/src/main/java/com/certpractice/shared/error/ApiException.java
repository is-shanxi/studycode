package com.certpractice.shared.error;

/**
 * Base exception for expected business and security failures. The detail text
 * is safe to expose to clients; internal exception messages must not be used.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(final ErrorCode errorCode, final String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public ApiException(final ErrorCode errorCode, final String detail, final Throwable cause) {
        super(detail, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
