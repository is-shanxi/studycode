package com.certpractice.shared.id;

import java.util.UUID;

import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;

/**
 * Parsing helpers for externally supplied UUIDs.
 */
public final class UuidUtils {

    private UuidUtils() {
    }

    public static UUID parse(final String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "UUID must not be blank");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Invalid UUID", exception);
        }
    }

    public static UUID parseOrNull(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parse(value);
    }
}
