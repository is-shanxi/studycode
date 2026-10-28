package com.certpractice.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChapterRepository extends JpaRepository<Chapter, UUID> {

    List<Chapter> findBySubjectIdOrderBySortOrderAsc(UUID subjectId);

    Optional<Chapter> findBySubjectIdAndCode(UUID subjectId, String code);
}
