package com.certpractice.community;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentReportRepository extends JpaRepository<ContentReport, UUID> {

    Page<ContentReport> findByStatusOrderByCreatedAtAsc(ContentReport.Status status, Pageable pageable);
}
