package com.certpractice.aigateway;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ai_usage_daily")
public class AiUsageDaily {

    @EmbeddedId
    private AiUsageDailyId id;

    @Column(name = "request_count", nullable = false)
    private int requestCount;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "actual_cost", nullable = false, precision = 14, scale = 6)
    private BigDecimal actualCost = BigDecimal.ZERO;
}
