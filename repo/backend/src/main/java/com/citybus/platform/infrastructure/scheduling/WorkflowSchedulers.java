package com.citybus.platform.infrastructure.scheduling;

import com.citybus.platform.application.PassengerService;
import com.citybus.platform.application.WorkflowService;
import com.citybus.platform.infrastructure.observability.TraceIdContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WorkflowSchedulers {
    private final WorkflowService workflowService;
    private final PassengerService passengerService;

    public WorkflowSchedulers(WorkflowService workflowService, PassengerService passengerService) {
        this.workflowService = workflowService;
        this.passengerService = passengerService;
    }

    @Scheduled(fixedDelay = 60000)
    public void releaseExpiredLeases() {
        String previousTraceId = TraceIdContext.current();
        TraceIdContext.currentOrCreate();
        try {
            workflowService.releaseExpiredLeases();
        } finally {
            TraceIdContext.restore(previousTraceId);
        }
    }

    @Scheduled(fixedDelay = 300000)
    public void escalateOldTasks() {
        String previousTraceId = TraceIdContext.current();
        TraceIdContext.currentOrCreate();
        try {
            workflowService.escalateOldTasks();
        } finally {
            TraceIdContext.restore(previousTraceId);
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void processReminderSchedules() {
        String previousTraceId = TraceIdContext.current();
        TraceIdContext.currentOrCreate();
        try {
            passengerService.processReminderSchedules();
        } finally {
            TraceIdContext.restore(previousTraceId);
        }
    }
}
