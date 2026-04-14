package com.citybus.platform.infrastructure.scheduling;

import com.citybus.platform.application.PassengerService;
import com.citybus.platform.application.WorkflowService;
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
        workflowService.releaseExpiredLeases();
    }

    @Scheduled(fixedDelay = 300000)
    public void escalateOldTasks() {
        workflowService.escalateOldTasks();
    }

    @Scheduled(fixedDelay = 60000)
    public void processReminderSchedules() {
        passengerService.processReminderSchedules();
    }
}
