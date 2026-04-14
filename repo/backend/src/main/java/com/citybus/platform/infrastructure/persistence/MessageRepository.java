package com.citybus.platform.infrastructure.persistence;

import com.citybus.platform.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<Message> findByIdAndUserId(UUID id, UUID userId);
}
