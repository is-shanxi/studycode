package com.certpractice.question;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportRowErrorRepository extends JpaRepository<ImportRowError, UUID> {

    List<ImportRowError> findByBatchIdOrderByRowNumberAsc(UUID batchId);

    void deleteByBatchId(UUID batchId);
}
