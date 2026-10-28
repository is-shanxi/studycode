package com.certpractice.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    Optional<Tag> findByNormalizedName(String normalizedName);

    List<Tag> findByStatusOrderByNameAsc(CatalogStatus status);
}
