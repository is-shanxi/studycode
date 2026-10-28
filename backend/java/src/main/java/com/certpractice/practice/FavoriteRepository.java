package com.certpractice.practice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteRepository extends JpaRepository<Favorite, FavoriteId> {

    List<Favorite> findByIdUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Favorite> findByIdUserIdAndIdQuestionId(UUID userId, UUID questionId);

    boolean existsByIdUserIdAndIdQuestionId(UUID userId, UUID questionId);
}
