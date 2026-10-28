package com.certpractice.practice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionItemRepository extends JpaRepository<SessionItem, UUID> {

    List<SessionItem> findBySessionIdOrderByOrdinalAsc(UUID sessionId);

    Optional<SessionItem> findByIdAndSessionId(UUID id, UUID sessionId);
}
