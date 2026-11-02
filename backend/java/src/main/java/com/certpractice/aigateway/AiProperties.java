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
        String provider,
        String model,
        Duration connectTimeout,
        Duration requestTimeout) {

    public AiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8000";
        }
        if (provider == null || provider.isBlank()) {
            provider = "mock";
        }
        if (model == null || model.isBlank()) {
            model = "mock-model";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (requestTimeout == null) {
            requestTimeout = Duration.ofSeconds(30);
        }
    }
}
