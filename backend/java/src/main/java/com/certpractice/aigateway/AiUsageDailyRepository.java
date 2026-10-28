package com.certpractice.aigateway;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiUsageDailyRepository extends JpaRepository<AiUsageDaily, AiUsageDailyId> {

    Optional<AiUsageDaily> findByIdUserIdAndIdUsageDate(UUID userId, LocalDate usageDate);
}
