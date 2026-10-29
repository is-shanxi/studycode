package com.certpractice.community;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscussionCommentRepository extends JpaRepository<DiscussionComment, UUID> {

    List<DiscussionComment> findByPostIdAndStatusOrderByCreatedAtAsc(
            UUID postId,
            DiscussionPost.Status status);

    List<DiscussionComment> findByPostIdOrderByCreatedAtAsc(UUID postId);

    List<DiscussionComment> findByParentCommentId(UUID parentCommentId);

    long countByPostIdAndStatus(UUID postId, DiscussionPost.Status status);
}
