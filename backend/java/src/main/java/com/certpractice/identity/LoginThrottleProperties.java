package com.certpractice.identity;

import java.time.Duration;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable login failure throttling. Failures are counted per normalized
 * username inside a rolling window; reaching the limit locks the username for
 * the configured duration. Deployments can tune or disable the throttle without
 * touching authentication code.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.login-throttle")
public class LoginThrottleProperties {

    private boolean enabled = true;
    private int maxFailures = 5;
    private Duration failureWindow = Duration.ofMinutes(15);
    private Duration lockDuration = Duration.ofMinutes(15);

    @PostConstruct
    public void validate() {
        if (maxFailures < 1) {
            throw new IllegalStateException(
                    "app.security.login-throttle.max-failures must be greater than 0");
        }
        requirePositive(failureWindow, "failure-window");
        requirePositive(lockDuration, "lock-duration");
    }

    private static void requirePositive(final Duration value, final String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(
                    "app.security.login-throttle." + name + " must be a positive duration");
        }
    }
}
