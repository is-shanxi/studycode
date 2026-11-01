package com.certpractice.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * In-process login failure throttle. The application runs as a single local
 * Java process, so no shared cache is required; the tracked-key map is bounded
 * to avoid unbounded growth from random usernames.
 */
@Service
public class LoginThrottleService {

    private static final int MAX_TRACKED_KEYS = 10_000;
    private static final String LOCKED_MESSAGE =
            "Too many failed login attempts; try again later";

    private final LoginThrottleProperties properties;
    private final Clock clock;
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginThrottleService(
            final LoginThrottleProperties properties,
            final Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void assertNotLocked(final String key) {
        if (!enabled(key)) {
            return;
        }
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return;
        }
        Instant now = clock.instant();
        if (attempt.isLocked(now)) {
            throw new ApiException(ErrorCode.LOGIN_LOCKED, LOCKED_MESSAGE);
        }
        if (attempt.lockedUntil() != null || attempt.isExpired(now, properties.getFailureWindow())) {
            attempts.remove(key, attempt);
        }
    }

    public void recordFailure(final String key) {
        if (!enabled(key)) {
            return;
        }
        pruneIfNeeded();
        Instant now = clock.instant();
        attempts.compute(key, (ignored, existing) -> {
            if (existing != null && existing.isLocked(now)) {
                return existing;
            }
            boolean lockExpired = existing != null
                    && existing.lockedUntil() != null
                    && !existing.isLocked(now);
            Attempt base = existing == null
                    || lockExpired
                    || existing.isExpired(now, properties.getFailureWindow())
                    ? Attempt.empty(now)
                    : existing;
            int failures = base.failures() + 1;
            Instant lockedUntil = failures >= properties.getMaxFailures()
                    ? now.plus(properties.getLockDuration())
                    : null;
            return new Attempt(base.windowStart(), failures, lockedUntil);
        });
    }

    public void recordSuccess(final String key) {
        if (key != null) {
            attempts.remove(key);
        }
    }

    private boolean enabled(final String key) {
        return properties.isEnabled() && key != null && !key.isBlank();
    }

    private void pruneIfNeeded() {
        if (attempts.size() < MAX_TRACKED_KEYS) {
            return;
        }
        Instant now = clock.instant();
        attempts.entrySet().removeIf(entry ->
                entry.getValue().lockedUntil() != null && !entry.getValue().isLocked(now)
                        || entry.getValue().isExpired(now, properties.getFailureWindow()));
        if (attempts.size() >= MAX_TRACKED_KEYS) {
            attempts.clear();
        }
    }

    private record Attempt(Instant windowStart, int failures, Instant lockedUntil) {

        static Attempt empty(final Instant now) {
            return new Attempt(now, 0, null);
        }

        boolean isLocked(final Instant now) {
            return lockedUntil != null && now.isBefore(lockedUntil);
        }

        boolean isExpired(final Instant now, final Duration window) {
            return now.isAfter(windowStart.plus(window));
        }
    }
}
