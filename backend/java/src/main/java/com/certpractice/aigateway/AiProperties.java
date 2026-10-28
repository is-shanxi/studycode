package com.certpractice.aigateway;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Internal AI service connection settings. Timeouts are mandatory in practice;
 * explicit defaults keep local test contexts deterministic.
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String baseUrl,
        String serviceToken,
        Duration connectTimeout,
        Duration requestTimeout) {

    public AiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8000";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (requestTimeout == null) {
            requestTimeout = Duration.ofSeconds(30);
        }
    }
}
