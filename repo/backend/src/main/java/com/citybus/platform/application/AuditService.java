package com.citybus.platform.application;

import com.citybus.platform.domain.AuditLog;
import com.citybus.platform.infrastructure.observability.TraceIdFilter;
import com.citybus.platform.infrastructure.persistence.AuditLogRepository;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(UUID actorUserId, String actionName, String targetType, String targetId, String details) {
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setActorUserId(actorUserId);
        log.setActionName(actionName);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetails(details);
        log.setTraceId(MDC.get(TraceIdFilter.TRACE_ID));
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }
}
