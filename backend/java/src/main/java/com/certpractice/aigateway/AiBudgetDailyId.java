package com.certpractice.aigateway;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode
@Embeddable
public class AiBudgetDailyId implements Serializable {

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 16)
    private AiBudgetDaily.Scope scope;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;
}
