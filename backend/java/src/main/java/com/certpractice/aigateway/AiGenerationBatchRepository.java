package com.certpractice.aigateway;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiGenerationBatchRepository extends JpaRepository<AiGenerationBatch, UUID> {

    List<AiGenerationBatch> findByStatusOrderByCreatedAtAsc(AiGenerationBatch.Status status);

    Page<AiGenerationBatch> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
