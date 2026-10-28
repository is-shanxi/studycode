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
@Table(name = "ai_budget_daily")
public class AiBudgetDaily {

    @EmbeddedId
    private AiBudgetDailyId id;

    @Column(name = "cost_limit", nullable = false, precision = 14, scale = 6)
    private BigDecimal costLimit;

    @Column(name = "cost_used", nullable = false, precision = 14, scale = 6)
    private BigDecimal costUsed = BigDecimal.ZERO;

    public enum Scope {
        TEAM
    }
}
