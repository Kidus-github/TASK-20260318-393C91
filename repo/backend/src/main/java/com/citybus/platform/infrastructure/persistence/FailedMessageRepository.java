package com.citybus.platform.infrastructure.persistence;

import com.citybus.platform.domain.FailedMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FailedMessageRepository extends JpaRepository<FailedMessage, UUID> {
}
