package com.certpractice.practice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WrongQuestionRepository extends JpaRepository<WrongQuestion, UUID> {

    List<WrongQuestion> findByUserIdAndActiveTrueOrderByLastFailedAtDesc(UUID userId);

    Optional<WrongQuestion> findByUserIdAndQuestionIdAndActiveTrue(UUID userId, UUID questionId);

    Optional<WrongQuestion> findByUserIdAndQuestionId(UUID userId, UUID questionId);
}
