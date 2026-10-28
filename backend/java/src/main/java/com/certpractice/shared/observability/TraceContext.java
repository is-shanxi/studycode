package com.certpractice.shared.observability;

import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;

/**
 * Central trace ID handling. Trace IDs are propagated through MDC and the
 * X-Trace-Id response header, and are safe to include in logs and responses.
 */
public final class TraceContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "traceId";

    private static final Pattern VALID_TRACE_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    private TraceContext() {
    }

    public static String currentTraceId() {
        return MDC.get(MDC_KEY);
    }

    public static String resolve(final String... candidates) {
        for (String candidate : candidates) {
            String valid = validOrNull(candidate);
            if (valid != null) {
                return valid;
            }
        }
        return UUID.randomUUID().toString();
    }

    public static String validOrNull(final String value) {
        if (value == null || !VALID_TRACE_ID.matcher(value).matches()) {
            return null;
        }
        return value;
    }
}
