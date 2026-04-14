package com.citybus.platform.application;

import com.citybus.platform.api.dto.WorkflowDtos;
import com.citybus.platform.domain.ApprovalTask;
import com.citybus.platform.domain.ApprovalTaskState;
import com.citybus.platform.domain.WorkflowDecision;
import com.citybus.platform.domain.WorkflowRule;
import com.citybus.platform.domain.WorkflowState;
import com.citybus.platform.domain.WorkflowTask;
import com.citybus.platform.infrastructure.persistence.ApprovalTaskRepository;
import com.citybus.platform.infrastructure.persistence.WorkflowDecisionRepository;
import com.citybus.platform.infrastructure.persistence.WorkflowRuleRepository;
import com.citybus.platform.infrastructure.persistence.WorkflowTaskRepository;
import com.citybus.platform.infrastructure.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkflowService {
    private final WorkflowTaskRepository workflowTaskRepository;
    private final ApprovalTaskRepository approvalTaskRepository;
    private final WorkflowRuleRepository workflowRuleRepository;
    private final WorkflowDecisionRepository workflowDecisionRepository;
    private final AuditService auditService;
    private final AlertService alertService;
    private final DiagnosticReportService diagnosticReportService;

    public WorkflowService(
            WorkflowTaskRepository workflowTaskRepository,
            ApprovalTaskRepository approvalTaskRepository,
            WorkflowRuleRepository workflowRuleRepository,
            WorkflowDecisionRepository workflowDecisionRepository,
            AuditService auditService,
            AlertService alertService,
            DiagnosticReportService diagnosticReportService
    ) {
        this.workflowTaskRepository = workflowTaskRepository;
        this.approvalTaskRepository = approvalTaskRepository;
        this.workflowRuleRepository = workflowRuleRepository;
        this.workflowDecisionRepository = workflowDecisionRepository;
        this.auditService = auditService;
        this.alertService = alertService;
        this.diagnosticReportService = diagnosticReportService;
    }

    @Transactional(readOnly = true)
    public List<WorkflowDtos.TaskResponse> listTasks(AuthenticatedUser currentUser) {
        List<WorkflowTask> tasks = workflowTaskRepository.findByOwnerRoleAndStateInOrderByUpdatedAtDesc(
                currentUser.roleName().name(),
                List.of(WorkflowState.PENDING, WorkflowState.LEASED, WorkflowState.ESCALATED, WorkflowState.RETURNED)
        );
        List<WorkflowDtos.TaskResponse> responses = new ArrayList<>();
        for (WorkflowTask task : tasks) {
            if (task.getRequiredApprovals() > 1 && task.getState() != WorkflowState.COMPLETED && task.getState() != WorkflowState.REJECTED) {
                ensureApprovalTasks(task);
                List<ApprovalTask> approvalTasks = approvalTaskRepository.findByParentTaskId(task.getId()).stream()
                        .filter(item -> item.getApproverRole().equalsIgnoreCase(currentUser.roleName().name()))
                        .filter(item -> List.of(ApprovalTaskState.PENDING, ApprovalTaskState.LEASED, ApprovalTaskState.RETURNED).contains(item.getState()))
                        .toList();
                for (ApprovalTask approvalTask : approvalTasks) {
                    if (isApprovalAccessible(approvalTask, currentUser, false)) {
                        responses.add(toResponse(approvalTask, task));
                    }
                }
                continue;
            }
            if (isTaskAccessible(task, currentUser, false)) {
                responses.add(toResponse(task));
            }
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public WorkflowDtos.TaskResponse getTask(AuthenticatedUser currentUser, UUID taskId) {
        ApprovalTask approvalTask = approvalTaskRepository.findByIdAndApproverRole(taskId, currentUser.roleName().name()).orElse(null);
        if (approvalTask != null) {
            if (!isApprovalAccessible(approvalTask, currentUser, false)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Approval task belongs to another dispatcher");
            }
            WorkflowTask parent = workflowTaskRepository.findById(approvalTask.getParentTaskId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Parent task not found"));
            return toResponse(approvalTask, parent);
        }

        WorkflowTask task = workflowTaskRepository.findByIdAndOwnerRole(taskId, currentUser.roleName().name())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Task not found"));
        if (!isTaskAccessible(task, currentUser, false)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Task belongs to another dispatcher");
        }
        return toResponse(task);
    }

    @Transactional
    public WorkflowDtos.TaskResponse claimTask(AuthenticatedUser currentUser, UUID taskId) {
        ApprovalTask approvalTask = approvalTaskRepository.findByIdAndApproverRole(taskId, currentUser.roleName().name()).orElse(null);
        if (approvalTask != null) {
            return claimApprovalTask(currentUser, approvalTask);
        }

        WorkflowTask task = workflowTaskRepository.findByIdAndOwnerRole(taskId, currentUser.roleName().name())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Task not found"));
        if (task.getRequiredApprovals() > 1 && task.getState() != WorkflowState.COMPLETED && task.getState() != WorkflowState.REJECTED) {
            ensureApprovalTasks(task);
            ApprovalTask nextApprovalTask = approvalTaskRepository.findByParentTaskId(task.getId()).stream()
                    .filter(item -> item.getApproverRole().equalsIgnoreCase(currentUser.roleName().name()))
                    .filter(item -> List.of(ApprovalTaskState.PENDING, ApprovalTaskState.LEASED, ApprovalTaskState.RETURNED).contains(item.getState()))
                    .filter(item -> isApprovalAccessible(item, currentUser, true))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "No claimable approval child task available"));
            return claimApprovalTask(currentUser, nextApprovalTask);
        }
        if (!isTaskAccessible(task, currentUser, true)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Task belongs to another dispatcher");
        }
        if (task.getState() == WorkflowState.REJECTED || task.getState() == WorkflowState.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "Task already finalized");
        }
        if (task.getLeaseOwnerUserId() != null && !currentUser.userId().equals(task.getLeaseOwnerUserId())
                && task.getLeaseExpiresAt() != null && task.getLeaseExpiresAt().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "Task is currently leased by another dispatcher");
        }
        task.setState(WorkflowState.LEASED);
        task.setLeaseOwnerUserId(currentUser.userId());
        task.setAssignedUserId(currentUser.userId());
        task.setLeaseExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        task.setUpdatedAt(Instant.now());
        workflowTaskRepository.save(task);
        return toResponse(task);
    }

    @Transactional
    public WorkflowDtos.TaskResponse decide(AuthenticatedUser currentUser, UUID taskId, WorkflowDtos.DecisionRequest request) {
        ApprovalTask approvalTask = approvalTaskRepository.findByIdAndApproverRole(taskId, currentUser.roleName().name()).orElse(null);
        if (approvalTask != null) {
            return decideApprovalTask(currentUser, approvalTask, request);
        }
        return decideParentTask(currentUser, taskId, request);
    }

    @Transactional
    public WorkflowDtos.BatchDecisionResponse batchDecide(AuthenticatedUser currentUser, WorkflowDtos.BatchDecisionRequest request) {
        List<WorkflowDtos.TaskResponse> succeeded = new ArrayList<>();
        List<WorkflowDtos.BatchDecisionFailure> failed = new ArrayList<>();
        for (String rawTaskId : request.taskIds()) {
            try {
                succeeded.add(decide(currentUser, UUID.fromString(rawTaskId), new WorkflowDtos.DecisionRequest(request.decision(), request.comment())));
            } catch (RuntimeException exception) {
                failed.add(new WorkflowDtos.BatchDecisionFailure(rawTaskId, exception.getMessage()));
            }
        }
        return new WorkflowDtos.BatchDecisionResponse(succeeded, failed);
    }

    @Transactional
    public WorkflowDtos.TaskResponse resubmit(AuthenticatedUser currentUser, UUID taskId, String comment) {
        ApprovalTask approvalTask = approvalTaskRepository.findByIdAndApproverRole(taskId, currentUser.roleName().name()).orElse(null);
        if (approvalTask != null) {
            return resubmitApprovalTask(currentUser, approvalTask, comment);
        }

        WorkflowTask task = workflowTaskRepository.findByIdAndOwnerRole(taskId, currentUser.roleName().name())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Task not found"));
        if (!isTaskAccessible(task, currentUser, false)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Task belongs to another dispatcher");
        }
        if (task.getState() != WorkflowState.RETURNED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only returned tasks can be resubmitted");
        }
        task.setState(task.isEscalated() ? WorkflowState.ESCALATED : WorkflowState.PENDING);
        task.setResubmissionCount(task.getResubmissionCount() + 1);
        task.setUpdatedAt(Instant.now());
        workflowTaskRepository.save(task);
        auditService.log(currentUser.userId(), "WORKFLOW_RESUBMITTED", "TASK", taskId.toString(), comment);
        return toResponse(task);
    }

    @Transactional
    public void releaseExpiredLeases() {
        workflowTaskRepository.findByLeaseExpiresAtBeforeAndState(Instant.now(), WorkflowState.LEASED)
                .forEach(task -> {
                    task.setState(task.isEscalated() ? WorkflowState.ESCALATED : WorkflowState.PENDING);
                    task.setAssignedUserId(null);
                    task.setLeaseOwnerUserId(null);
                    task.setLeaseExpiresAt(null);
                    task.setUpdatedAt(Instant.now());
                    workflowTaskRepository.save(task);
                });
        approvalTaskRepository.findByLeaseExpiresAtBeforeAndState(Instant.now(), ApprovalTaskState.LEASED)
                .forEach(task -> {
                    task.setState(ApprovalTaskState.PENDING);
                    task.setAssignedUserId(null);
                    task.setLeaseOwnerUserId(null);
                    task.setLeaseExpiresAt(null);
                    task.setUpdatedAt(Instant.now());
                    approvalTaskRepository.save(task);
                });
    }

    @Transactional
    public void escalateOldTasks() {
        workflowTaskRepository.findByCreatedAtBeforeAndState(Instant.now().minus(24, ChronoUnit.HOURS), WorkflowState.PENDING)
                .forEach(task -> {
                    task.setState(WorkflowState.ESCALATED);
                    task.setEscalated(true);
                    task.setUpdatedAt(Instant.now());
                    workflowTaskRepository.save(task);
                    var report = diagnosticReportService.createReport(
                            "FAILED_WORKFLOW_TASKS",
                            Map.of("taskId", task.getId().toString(), "reason", "TIMEOUT_ESCALATION", "taskType", task.getTaskType()),
                            null
                    );
                    alertService.createAlert(
                            "WORKFLOW_TIMEOUT",
                            "WARN",
                            "Workflow task exceeded 24 hour processing window",
                            "Task " + task.getId() + " (" + task.getTitle() + ") escalated after timeout (reportId=" + report.getId() + ")",
                            null
                    );
                });
    }

    private WorkflowDtos.TaskResponse claimApprovalTask(AuthenticatedUser currentUser, ApprovalTask approvalTask) {
        if (!isApprovalAccessible(approvalTask, currentUser, true)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Approval task belongs to another dispatcher");
        }
        if (List.of(ApprovalTaskState.APPROVED, ApprovalTaskState.REJECTED).contains(approvalTask.getState())) {
            throw new ApiException(HttpStatus.CONFLICT, "Task already finalized");
        }
        if (approvalTask.getLeaseOwnerUserId() != null && !currentUser.userId().equals(approvalTask.getLeaseOwnerUserId())
                && approvalTask.getLeaseExpiresAt() != null && approvalTask.getLeaseExpiresAt().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "Task is currently leased by another dispatcher");
        }
        approvalTask.setState(ApprovalTaskState.LEASED);
        approvalTask.setLeaseOwnerUserId(currentUser.userId());
        approvalTask.setAssignedUserId(currentUser.userId());
        approvalTask.setLeaseExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        approvalTask.setUpdatedAt(Instant.now());
        approvalTaskRepository.save(approvalTask);
        WorkflowTask parent = workflowTaskRepository.findById(approvalTask.getParentTaskId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Parent task not found"));
        return toResponse(approvalTask, parent);
    }

    private WorkflowDtos.TaskResponse decideApprovalTask(AuthenticatedUser currentUser, ApprovalTask approvalTask, WorkflowDtos.DecisionRequest request) {
        if (!isApprovalAccessible(approvalTask, currentUser, false)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Approval task belongs to another dispatcher");
        }
        if (approvalTask.getLeaseOwnerUserId() == null || !approvalTask.getLeaseOwnerUserId().equals(currentUser.userId())
                || approvalTask.getLeaseExpiresAt() == null || approvalTask.getLeaseExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "Task lease expired");
        }
        String decision = request.decision().toUpperCase();
        if (!List.of("APPROVE", "REJECT", "RETURN").contains(decision)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Unsupported decision");
        }
        if ("APPROVE".equals(decision)
                && approvalTaskRepository.existsByParentTaskIdAndDecidedByUserIdAndState(approvalTask.getParentTaskId(), currentUser.userId(), ApprovalTaskState.APPROVED)) {
            throw new ApiException(HttpStatus.CONFLICT, "Same dispatcher cannot provide multiple approvals for the same task");
        }

        WorkflowTask parent = workflowTaskRepository.findById(approvalTask.getParentTaskId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Parent task not found"));
        approvalTask.setState(toApprovalState(decision));
        approvalTask.setDecidedByUserId(currentUser.userId());
        approvalTask.setDecisionComment(request.comment());
        approvalTask.setLeaseOwnerUserId(null);
        approvalTask.setLeaseExpiresAt(null);
        approvalTask.setUpdatedAt(Instant.now());
        approvalTaskRepository.save(approvalTask);

        WorkflowDecision workflowDecision = new WorkflowDecision();
        workflowDecision.setId(UUID.randomUUID());
        workflowDecision.setTaskId(parent.getId());
        workflowDecision.setDecidedByUserId(currentUser.userId());
        workflowDecision.setDecision(decision);
        workflowDecision.setCommentText(request.comment());
        workflowDecision.setCreatedAt(Instant.now());
        workflowDecisionRepository.save(workflowDecision);

        List<WorkflowTask> followUpTasks = updateParentByApprovalOutcome(parent, decision);
        workflowTaskRepository.save(parent);
        if (!followUpTasks.isEmpty()) {
            workflowTaskRepository.saveAll(followUpTasks);
        }
        auditService.log(currentUser.userId(), "WORKFLOW_DECISION_" + decision, "APPROVAL_TASK", approvalTask.getId().toString(), request.comment());
        return toResponse(approvalTask, parent);
    }

    private WorkflowDtos.TaskResponse decideParentTask(AuthenticatedUser currentUser, UUID taskId, WorkflowDtos.DecisionRequest request) {
        WorkflowTask task = workflowTaskRepository.findByIdAndOwnerRole(taskId, currentUser.roleName().name())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Task not found"));
        if (!isTaskAccessible(task, currentUser, false)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Task belongs to another dispatcher");
        }
        if (task.getLeaseOwnerUserId() == null || !task.getLeaseOwnerUserId().equals(currentUser.userId())
                || task.getLeaseExpiresAt() == null || task.getLeaseExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "Task lease expired");
        }
        if (task.getState() == WorkflowState.REJECTED || task.getState() == WorkflowState.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "Task is no longer pending");
        }
        String decision = request.decision().toUpperCase();
        if (task.getRequiredApprovals() > 1 && "APPROVE".equals(decision)) {
            if (workflowDecisionRepository.existsByTaskIdAndDecidedByUserIdAndDecision(task.getId(), currentUser.userId(), "APPROVE")) {
                throw new ApiException(HttpStatus.CONFLICT, "Same dispatcher cannot provide multiple approvals for the same task");
            }
        }
        List<WorkflowTask> followUpTasks = applyParentDecision(task, decision);
        task.setLeaseOwnerUserId(null);
        task.setLeaseExpiresAt(null);
        if ("APPROVE".equals(decision) && task.getState() != WorkflowState.COMPLETED) {
            task.setAssignedUserId(null);
        }
        task.setUpdatedAt(Instant.now());
        workflowTaskRepository.save(task);
        if (!followUpTasks.isEmpty()) {
            workflowTaskRepository.saveAll(followUpTasks);
        }

        WorkflowDecision workflowDecision = new WorkflowDecision();
        workflowDecision.setId(UUID.randomUUID());
        workflowDecision.setTaskId(task.getId());
        workflowDecision.setDecidedByUserId(currentUser.userId());
        workflowDecision.setDecision(decision);
        workflowDecision.setCommentText(request.comment());
        workflowDecision.setCreatedAt(Instant.now());
        workflowDecisionRepository.save(workflowDecision);

        auditService.log(currentUser.userId(), "WORKFLOW_DECISION_" + decision, "TASK", taskId.toString(), request.comment());
        return toResponse(task);
    }

    private WorkflowDtos.TaskResponse resubmitApprovalTask(AuthenticatedUser currentUser, ApprovalTask approvalTask, String comment) {
        if (!isApprovalAccessible(approvalTask, currentUser, false)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Approval task belongs to another dispatcher");
        }
        if (approvalTask.getState() != ApprovalTaskState.RETURNED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only returned approval tasks can be resubmitted");
        }
        approvalTask.setState(ApprovalTaskState.PENDING);
        approvalTask.setAssignedUserId(null);
        approvalTask.setLeaseOwnerUserId(null);
        approvalTask.setLeaseExpiresAt(null);
        approvalTask.setUpdatedAt(Instant.now());
        approvalTaskRepository.save(approvalTask);

        WorkflowTask parent = workflowTaskRepository.findById(approvalTask.getParentTaskId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Parent task not found"));
        parent.setState(parent.isEscalated() ? WorkflowState.ESCALATED : WorkflowState.PENDING);
        parent.setResubmissionCount(parent.getResubmissionCount() + 1);
        parent.setUpdatedAt(Instant.now());
        workflowTaskRepository.save(parent);
        auditService.log(currentUser.userId(), "WORKFLOW_RESUBMITTED", "APPROVAL_TASK", approvalTask.getId().toString(), comment);
        return toResponse(approvalTask, parent);
    }

    private List<WorkflowTask> applyParentDecision(WorkflowTask task, String decision) {
        switch (decision) {
            case "APPROVE" -> {
                task.setApprovalCount(task.getApprovalCount() + 1);
                task.setCurrentApprovals(task.getApprovalCount());
                if (task.getApprovalCount() >= task.getRequiredApprovals()) {
                    task.setState(WorkflowState.COMPLETED);
                    task.setProgressStep(task.getProgressTotal());
                    return createFollowUpTasksByRules(task);
                }
                task.setState(task.isEscalated() ? WorkflowState.ESCALATED : WorkflowState.PENDING);
                task.setProgressStep(Math.min(task.getProgressTotal(), task.getApprovalCount() + 1));
            }
            case "REJECT" -> {
                task.setState(WorkflowState.REJECTED);
                diagnosticReportService.createReport(
                        "FAILED_WORKFLOW_TASKS",
                        Map.of("taskId", task.getId().toString(), "reason", "REJECTED", "taskType", task.getTaskType()),
                        null
                );
            }
            case "RETURN" -> task.setState(WorkflowState.RETURNED);
            default -> throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Unsupported decision");
        }
        return List.of();
    }

    private List<WorkflowTask> updateParentByApprovalOutcome(WorkflowTask parent, String decision) {
        if ("APPROVE".equals(decision)) {
            long approvedCount = approvalTaskRepository.countByParentTaskIdAndState(parent.getId(), ApprovalTaskState.APPROVED);
            parent.setCurrentApprovals((int) approvedCount);
            parent.setApprovalCount((int) approvedCount);
            parent.setProgressStep(Math.min(parent.getProgressTotal(), Math.max(1, (int) approvedCount)));
            if (approvedCount >= parent.getRequiredApprovals()) {
                parent.setState(WorkflowState.COMPLETED);
                parent.setProgressStep(parent.getProgressTotal());
                return createFollowUpTasksByRules(parent);
            }
            parent.setState(parent.isEscalated() ? WorkflowState.ESCALATED : WorkflowState.PENDING);
            return List.of();
        }
        if ("REJECT".equals(decision)) {
            parent.setState(WorkflowState.REJECTED);
            diagnosticReportService.createReport(
                    "FAILED_WORKFLOW_TASKS",
                    Map.of("taskId", parent.getId().toString(), "reason", "REJECTED", "taskType", parent.getTaskType()),
                    null
            );
            return List.of();
        }
        parent.setState(WorkflowState.RETURNED);
        return List.of();
    }

    private List<WorkflowTask> createFollowUpTasksByRules(WorkflowTask task) {
        Map<String, String> payloadFields = parsePayload(task.getPayload());
        List<WorkflowRule> matchedRules = workflowRuleRepository.findByTaskTypeIgnoreCaseAndEnabledTrueOrderByPriorityAsc(task.getTaskType()).stream()
                .filter(rule -> rule.getExpectedValue().equalsIgnoreCase(payloadFields.getOrDefault(rule.getTriggerField(), "")))
                .toList();
        List<WorkflowTask> followUpTasks = new ArrayList<>();
        for (WorkflowRule rule : matchedRules) {
            for (String nextTaskType : rule.getNextTaskTypes().split(",")) {
                String normalizedTaskType = nextTaskType.trim();
                if (normalizedTaskType.isBlank()) {
                    continue;
                }
                WorkflowTask followUp = new WorkflowTask();
                followUp.setId(UUID.randomUUID());
                followUp.setTitle(task.getTitle() + " - " + normalizedTaskType.replace('_', ' '));
                followUp.setTaskType(normalizedTaskType);
                followUp.setState(task.isEscalated() ? WorkflowState.ESCALATED : WorkflowState.PENDING);
                followUp.setOwnerRole(task.getOwnerRole());
                followUp.setApprovalMode("ANY");
                followUp.setRequiredApprovals(1);
                followUp.setApprovalCount(0);
                followUp.setCurrentApprovals(0);
                followUp.setProgressStep(1);
                followUp.setProgressTotal(1);
                followUp.setEscalated(task.isEscalated());
                followUp.setResubmissionCount(0);
                followUp.setParentTaskId(task.getId());
                followUp.setPayload(appendProgressNote(task.getPayload(), "parentTaskId=" + task.getId()));
                followUp.setCreatedAt(Instant.now());
                followUp.setUpdatedAt(Instant.now());
                followUpTasks.add(followUp);
            }
        }
        return followUpTasks;
    }

    private void ensureApprovalTasks(WorkflowTask parent) {
        if (parent.getRequiredApprovals() <= 1) {
            return;
        }
        if (!approvalTaskRepository.findByParentTaskId(parent.getId()).isEmpty()) {
            return;
        }
        UUID groupId = parent.getApprovalGroupId() == null ? UUID.randomUUID() : parent.getApprovalGroupId();
        parent.setApprovalGroupId(groupId);
        parent.setCurrentApprovals(0);
        parent.setUpdatedAt(Instant.now());
        workflowTaskRepository.save(parent);
        List<ApprovalTask> approvals = new ArrayList<>();
        for (int i = 0; i < parent.getRequiredApprovals(); i++) {
            ApprovalTask child = new ApprovalTask();
            child.setId(UUID.randomUUID());
            child.setParentTaskId(parent.getId());
            child.setApprovalGroupId(groupId);
            child.setApproverRole(parent.getOwnerRole());
            child.setState(ApprovalTaskState.PENDING);
            child.setCreatedAt(Instant.now());
            child.setUpdatedAt(Instant.now());
            approvals.add(child);
        }
        approvalTaskRepository.saveAll(approvals);
    }

    private ApprovalTaskState toApprovalState(String decision) {
        return switch (decision) {
            case "APPROVE" -> ApprovalTaskState.APPROVED;
            case "REJECT" -> ApprovalTaskState.REJECTED;
            case "RETURN" -> ApprovalTaskState.RETURNED;
            default -> throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Unsupported decision");
        };
    }

    private boolean isTaskAccessible(WorkflowTask task, AuthenticatedUser currentUser, boolean allowClaimFromPool) {
        if (task.getAssignedUserId() == null) {
            return true;
        }
        if (task.getAssignedUserId().equals(currentUser.userId())) {
            return true;
        }
        return allowClaimFromPool && task.getLeaseExpiresAt() != null && task.getLeaseExpiresAt().isBefore(Instant.now());
    }

    private boolean isApprovalAccessible(ApprovalTask task, AuthenticatedUser currentUser, boolean allowClaimFromPool) {
        if (task.getAssignedUserId() == null) {
            return true;
        }
        if (task.getAssignedUserId().equals(currentUser.userId())) {
            return true;
        }
        return allowClaimFromPool && task.getLeaseExpiresAt() != null && task.getLeaseExpiresAt().isBefore(Instant.now());
    }

    private Map<String, String> parsePayload(String payload) {
        Map<String, String> parsed = new HashMap<>();
        if (payload == null || payload.isBlank()) {
            return parsed;
        }
        for (String token : payload.split("[;|]")) {
            int separatorIndex = token.indexOf('=');
            if (separatorIndex <= 0) {
                continue;
            }
            String key = token.substring(0, separatorIndex).trim();
            String value = token.substring(separatorIndex + 1).trim();
            if (!key.isBlank()) {
                parsed.put(key, value);
            }
        }
        return parsed;
    }

    private String appendProgressNote(String payload, String note) {
        if (payload == null || payload.isBlank()) {
            return note;
        }
        if (payload.contains(note)) {
            return payload;
        }
        return payload + "|" + note;
    }

    private WorkflowDtos.TaskResponse toResponse(WorkflowTask task) {
        return new WorkflowDtos.TaskResponse(
                task.getId().toString(),
                task.getTitle(),
                task.getTaskType(),
                task.getState().name(),
                task.getLeaseExpiresAt(),
                task.getPayload(),
                task.getParentTaskId() == null ? null : task.getParentTaskId().toString(),
                task.getApprovalGroupId() == null ? null : task.getApprovalGroupId().toString(),
                task.getApprovalMode(),
                task.getRequiredApprovals(),
                task.getApprovalCount(),
                task.getCurrentApprovals(),
                task.getProgressStep(),
                task.getProgressTotal(),
                task.getResubmissionCount(),
                task.isEscalated()
        );
    }

    private WorkflowDtos.TaskResponse toResponse(ApprovalTask approvalTask, WorkflowTask parent) {
        return new WorkflowDtos.TaskResponse(
                approvalTask.getId().toString(),
                parent.getTitle() + " - Parallel Approval",
                parent.getTaskType() + "_APPROVAL",
                approvalTask.getState().name(),
                approvalTask.getLeaseExpiresAt(),
                parent.getPayload(),
                parent.getId().toString(),
                approvalTask.getApprovalGroupId().toString(),
                parent.getApprovalMode(),
                parent.getRequiredApprovals(),
                parent.getApprovalCount(),
                parent.getCurrentApprovals(),
                parent.getProgressStep(),
                parent.getProgressTotal(),
                parent.getResubmissionCount(),
                parent.isEscalated()
        );
    }
}
