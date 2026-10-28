package com.certpractice.question;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewEventRepository extends JpaRepository<ReviewEvent, UUID> {

    List<ReviewEvent> findByQuestionVersionIdOrderByCreatedAtAsc(UUID questionVersionId);
}
