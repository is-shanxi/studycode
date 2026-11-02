package com.certpractice.aigateway;

import java.math.BigDecimal;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Default server-side quota and budget limits. Administrators can override
 * these at runtime through versioned system configuration.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.ai.quota")
public class AiQuotaProperties {

    private int dailyRequestLimit = 10;
    private int sessionTurnLimit = 20;
    private int maxInputChars = 2000;
    private BigDecimal teamDailyBudget = new BigDecimal("1.0");
    private BigDecimal reservedCost = new BigDecimal("0.001");

    @PostConstruct
    public void validate() {
        if (dailyRequestLimit < 1) {
            throw new IllegalStateException("app.ai.quota.daily-request-limit must be positive");
        }
        if (sessionTurnLimit < 1) {
            throw new IllegalStateException("app.ai.quota.session-turn-limit must be positive");
        }
        if (maxInputChars < 1) {
            throw new IllegalStateException("app.ai.quota.max-input-chars must be positive");
        }
        if (teamDailyBudget == null || teamDailyBudget.signum() < 0) {
            throw new IllegalStateException("app.ai.quota.team-daily-budget must not be negative");
        }
        if (reservedCost == null || reservedCost.signum() <= 0) {
            throw new IllegalStateException("app.ai.quota.reserved-cost must be positive");
        }
    }
}
