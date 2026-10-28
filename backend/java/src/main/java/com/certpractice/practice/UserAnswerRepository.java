package com.certpractice.practice;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAnswerRepository extends JpaRepository<UserAnswer, UUID> {

    Optional<UserAnswer> findBySessionItemId(UUID sessionItemId);
}
