package com.citybus.platform.infrastructure.persistence;

import com.citybus.platform.domain.SystemAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SystemAlertRepository extends JpaRepository<SystemAlert, UUID> {
    List<SystemAlert> findTop20ByOrderByCreatedAtDesc();
}
