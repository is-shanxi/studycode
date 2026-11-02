package com.certpractice.identity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * Deterministic time-based coverage for the login throttle. The integration
 * test covers HTTP behavior; this test pins the expiry and reset rules.
 */
class LoginThrottleServiceTest {

    private static final String USERNAME = "alice";
    private static final Instant START = Instant.parse("2026-11-01T00:00:00Z");

    @Test
    void locksAfterConfiguredFailuresAndReopensAfterLockDuration() {
        MutableClock clock = new MutableClock(START);
        LoginThrottleService service = service(clock, 2, Duration.ofMinutes(10), Duration.ofMinutes(5));

        service.recordFailure(USERNAME);
        assertDoesNotThrow(() -> service.assertNotLocked(USERNAME));
        service.recordFailure(USERNAME);

        ApiException locked = assertThrows(ApiException.class,
                () -> service.assertNotLocked(USERNAME));
        assertEquals(ErrorCode.LOGIN_LOCKED, locked.getErrorCode());

        clock.advance(Duration.ofMinutes(6));
        assertDoesNotThrow(() -> service.assertNotLocked(USERNAME));
        service.recordFailure(USERNAME);
        assertDoesNotThrow(() -> service.assertNotLocked(USERNAME));
    }

    @Test
    void successfulLoginResetsFailures() {
        MutableClock clock = new MutableClock(START);
        LoginThrottleService service = service(clock, 2, Duration.ofMinutes(10), Duration.ofMinutes(5));

        service.recordFailure(USERNAME);
        service.recordSuccess(USERNAME);
        service.recordFailure(USERNAME);

        assertDoesNotThrow(() -> service.assertNotLocked(USERNAME));
    }

    @Test
    void failuresExpireAfterTheWindow() {
        MutableClock clock = new MutableClock(START);
        LoginThrottleService service = service(clock, 2, Duration.ofMinutes(5), Duration.ofMinutes(5));

        service.recordFailure(USERNAME);
        clock.advance(Duration.ofMinutes(6));
        service.recordFailure(USERNAME);

        assertDoesNotThrow(() -> service.assertNotLocked(USERNAME));
    }

    @Test
    void disabledThrottleDoesNotLock() {
        MutableClock clock = new MutableClock(START);
        LoginThrottleProperties properties = properties(1, Duration.ofMinutes(5), Duration.ofMinutes(5));
        properties.setEnabled(false);
        LoginThrottleService service = new LoginThrottleService(properties, clock);

        service.recordFailure(USERNAME);
        service.recordFailure(USERNAME);

        assertDoesNotThrow(() -> service.assertNotLocked(USERNAME));
    }

    private LoginThrottleService service(
            final MutableClock clock,
            final int maxFailures,
            final Duration window,
            final Duration lockDuration) {
        return new LoginThrottleService(properties(maxFailures, window, lockDuration), clock);
    }

    private LoginThrottleProperties properties(
            final int maxFailures,
            final Duration window,
            final Duration lockDuration) {
        LoginThrottleProperties properties = new LoginThrottleProperties();
        properties.setMaxFailures(maxFailures);
        properties.setFailureWindow(window);
        properties.setLockDuration(lockDuration);
        return properties;
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(final Instant current) {
            this.current = current;
        }

        void advance(final Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
