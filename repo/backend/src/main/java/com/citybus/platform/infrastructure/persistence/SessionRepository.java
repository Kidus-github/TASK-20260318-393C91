package com.citybus.platform.infrastructure.persistence;

import com.citybus.platform.domain.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {
    List<SessionEntity> findByUserIdAndRevokedFalse(UUID userId);
    List<SessionEntity> findByExpiresAtBefore(Instant cutoff);
}
