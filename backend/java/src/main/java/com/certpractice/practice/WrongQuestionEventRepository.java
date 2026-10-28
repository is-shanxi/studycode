package com.certpractice.practice;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WrongQuestionEventRepository extends JpaRepository<WrongQuestionEvent, UUID> {

    List<WrongQuestionEvent> findByWrongQuestionIdOrderByCreatedAtDesc(UUID wrongQuestionId);
}
