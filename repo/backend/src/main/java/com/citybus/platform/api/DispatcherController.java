package com.citybus.platform.api;

import com.citybus.platform.api.dto.WorkflowDtos;
import com.citybus.platform.application.WorkflowService;
import com.citybus.platform.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dispatcher/tasks")
@PreAuthorize("hasRole('DISPATCHER')")
public class DispatcherController {
    private final WorkflowService workflowService;

    public DispatcherController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping
    public List<WorkflowDtos.TaskResponse> list(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return workflowService.listTasks(currentUser);
    }

    @GetMapping("/{id}")
    public WorkflowDtos.TaskResponse get(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable UUID id) {
        return workflowService.getTask(currentUser, id);
    }

    @PostMapping("/{id}/claim")
    public WorkflowDtos.TaskResponse claim(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable UUID id) {
        return workflowService.claimTask(currentUser, id);
    }

    @PostMapping("/{id}/decision")
    public WorkflowDtos.TaskResponse decide(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable UUID id, @Valid @RequestBody WorkflowDtos.DecisionRequest request) {
        return workflowService.decide(currentUser, id, request);
    }

    @PostMapping("/{id}/resubmit")
    public WorkflowDtos.TaskResponse resubmit(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable UUID id, @Valid @RequestBody WorkflowDtos.DecisionRequest request) {
        return workflowService.resubmit(currentUser, id, request.comment());
    }

    @PostMapping("/batch-decision")
    public WorkflowDtos.BatchDecisionResponse batchDecision(@AuthenticationPrincipal AuthenticatedUser currentUser, @Valid @RequestBody WorkflowDtos.BatchDecisionRequest request) {
        return workflowService.batchDecide(currentUser, request);
    }
}
