package com.certpractice.question;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CorrectionReportRepository extends JpaRepository<CorrectionReport, UUID> {

    List<CorrectionReport> findByStatusOrderByCreatedAtAsc(CorrectionReport.Status status);
}
