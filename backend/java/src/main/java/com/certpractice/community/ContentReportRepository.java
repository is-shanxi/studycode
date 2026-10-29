package com.certpractice.community;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentReportRepository extends JpaRepository<ContentReport, UUID> {

    Page<ContentReport> findByStatusOrderByCreatedAtAsc(ContentReport.Status status, Pageable pageable);

    List<ContentReport> findByStatusInOrderByCreatedAtAsc(Collection<ContentReport.Status> statuses);

    Optional<ContentReport> findFirstByReporterUserIdAndTargetTypeAndTargetIdAndStatusInOrderByCreatedAtAsc(
            UUID reporterUserId,
            ContentReport.TargetType targetType,
            UUID targetId,
            Collection<ContentReport.Status> statuses);
}
