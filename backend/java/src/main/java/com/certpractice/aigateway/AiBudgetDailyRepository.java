package com.certpractice.aigateway;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiBudgetDailyRepository extends JpaRepository<AiBudgetDaily, AiBudgetDailyId> {

    Optional<AiBudgetDaily> findByIdScopeAndIdUsageDate(AiBudgetDaily.Scope scope, LocalDate usageDate);
}
