package com.certpractice.question;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AiVariantSourceRepository extends JpaRepository<AiVariantSource, UUID> {

    Optional<AiVariantSource> findByGenerationCandidateId(UUID generationCandidateId);

    @Query("select source.questionVersionId from AiVariantSource source")
    Set<UUID> findAllVersionIds();
}
