package com.certpractice.shared.health;

import java.time.Clock;
import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public liveness endpoint. Actuator remains available at /actuator/health for
 * infrastructure probes; this endpoint is part of the versioned browser API.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final Clock clock;

    public HealthController(final Clock clock) {
        this.clock = clock;
    }

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("UP", "cert-practice-java", Instant.now(clock));
    }

    public record HealthResponse(String status, String service, Instant timestamp) {
    }
}
