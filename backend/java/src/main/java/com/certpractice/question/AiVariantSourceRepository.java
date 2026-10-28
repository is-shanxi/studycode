package com.certpractice.question;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiVariantSourceRepository extends JpaRepository<AiVariantSource, UUID> {

    Optional<AiVariantSource> findByGenerationCandidateId(UUID generationCandidateId);
}
