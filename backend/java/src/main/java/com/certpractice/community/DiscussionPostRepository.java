package com.certpractice.community;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscussionPostRepository extends JpaRepository<DiscussionPost, UUID> {

    Page<DiscussionPost> findByQuestionVersionIdAndStatusOrderByCreatedAtDesc(
            UUID questionVersionId,
            DiscussionPost.Status status,
            Pageable pageable);

    Page<DiscussionPost> findByQuestionIdOrderByCreatedAtDesc(UUID questionId, Pageable pageable);

    Optional<DiscussionPost> findByIdAndStatus(UUID id, DiscussionPost.Status status);
}
