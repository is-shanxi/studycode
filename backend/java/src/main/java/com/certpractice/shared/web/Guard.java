package com.certpractice.shared.web;

import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;

/**
 * Small validation helpers for invariants that are easier to express in code
 * than in bean validation annotations.
 */
public final class Guard {

    private Guard() {
    }

    public static void require(final boolean condition, final ErrorCode errorCode, final String message) {
        if (!condition) {
            throw new ApiException(errorCode, message);
        }
    }

    public static <T> T requireNonNull(final T value, final ErrorCode errorCode, final String message) {
        if (value == null) {
            throw new ApiException(errorCode, message);
        }
        return value;
    }
}
