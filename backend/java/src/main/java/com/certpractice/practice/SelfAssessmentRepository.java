package com.certpractice.practice;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SelfAssessmentRepository extends JpaRepository<SelfAssessment, UUID> {

    Optional<SelfAssessment> findBySessionItemId(UUID sessionItemId);

    List<SelfAssessment> findBySessionItemIdIn(Collection<UUID> sessionItemIds);
}
