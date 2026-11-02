package com.certpractice.aigateway;

import java.time.Duration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Database-leased generation scheduling. No message queue is involved; a
 * single local scheduler polls for claimable batches.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.ai.generation")
public class AiGenerationProperties {

    private boolean schedulerEnabled = true;
    private long pollDelayMs = 5000;
    private Duration leaseDuration = Duration.ofMinutes(2);
    private int maxAttempts = 3;
    private int defaultTargetCount = 30;
    private String promptVersion = "v1";
}
