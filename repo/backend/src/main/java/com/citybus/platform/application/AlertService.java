package com.citybus.platform.application;

import com.citybus.platform.domain.SystemAlert;
import com.citybus.platform.infrastructure.persistence.SystemAlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AlertService {
    private final SystemAlertRepository systemAlertRepository;

    public AlertService(SystemAlertRepository systemAlertRepository) {
        this.systemAlertRepository = systemAlertRepository;
    }

    @Transactional
    public void createAlert(String alertType, String severity, String summary, String details, String traceId) {
        SystemAlert alert = new SystemAlert();
        alert.setId(UUID.randomUUID());
        alert.setAlertType(alertType);
        alert.setSeverity(severity);
        alert.setSummary(summary);
        alert.setDetails(details);
        alert.setTraceId(traceId);
        alert.setCreatedAt(Instant.now());
        systemAlertRepository.save(alert);
    }
}
