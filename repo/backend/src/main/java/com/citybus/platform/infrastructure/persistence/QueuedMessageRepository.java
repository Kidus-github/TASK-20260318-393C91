package com.citybus.platform.infrastructure.persistence;

import com.citybus.platform.domain.QueueStatus;
import com.citybus.platform.domain.QueuedMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueuedMessageRepository extends JpaRepository<QueuedMessage, UUID> {
    List<QueuedMessage> findTop50ByStatusAndScheduledAtBeforeOrderByScheduledAtAsc(QueueStatus status, Instant scheduledAt);
    Optional<QueuedMessage> findByIdempotencyKey(String idempotencyKey);
    long countByStatus(QueueStatus status);
}
