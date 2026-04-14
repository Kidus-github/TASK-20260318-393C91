package com.citybus.platform.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.Instant;
import java.util.List;

public final class WorkflowDtos {
    private WorkflowDtos() {
    }

    public record TaskResponse(
            String id,
            String title,
            String taskType,
            String state,
            Instant leaseExpiresAt,
            String payload,
            String parentTaskId,
            String approvalGroupId,
            String approvalMode,
            int requiredApprovals,
            int approvalCount,
            int currentApprovals,
            int progressStep,
            int progressTotal,
            int resubmissionCount,
            boolean escalated
    ) {}

    public record DecisionRequest(
            @NotBlank String decision,
            String comment
    ) {}

    public record BatchDecisionRequest(
            @NotEmpty List<String> taskIds,
            @NotBlank String decision,
            String comment
    ) {}

    public record BatchDecisionResponse(
            List<TaskResponse> succeeded,
            List<BatchDecisionFailure> failed
    ) {}

    public record BatchDecisionFailure(String taskId, String message) {}
}
