package tests.unit_tests;

import com.citybus.platform.api.dto.WorkflowDtos;
import com.citybus.platform.application.AlertService;
import com.citybus.platform.application.ApiException;
import com.citybus.platform.application.AuditService;
import com.citybus.platform.application.DiagnosticReportService;
import com.citybus.platform.application.WorkflowService;
import com.citybus.platform.domain.ApprovalTask;
import com.citybus.platform.domain.ApprovalTaskState;
import com.citybus.platform.domain.RoleName;
import com.citybus.platform.domain.WorkflowRule;
import com.citybus.platform.domain.WorkflowState;
import com.citybus.platform.domain.WorkflowTask;
import com.citybus.platform.infrastructure.persistence.ApprovalTaskRepository;
import com.citybus.platform.infrastructure.persistence.WorkflowDecisionRepository;
import com.citybus.platform.infrastructure.persistence.WorkflowRuleRepository;
import com.citybus.platform.infrastructure.persistence.WorkflowTaskRepository;
import com.citybus.platform.infrastructure.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceUnitTest {
    @Mock
    private WorkflowTaskRepository workflowTaskRepository;
    @Mock
    private ApprovalTaskRepository approvalTaskRepository;
    @Mock
    private WorkflowRuleRepository workflowRuleRepository;
    @Mock
    private WorkflowDecisionRepository workflowDecisionRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private AlertService alertService;
    @Mock
    private DiagnosticReportService diagnosticReportService;

    private WorkflowService workflowService;
    private AuthenticatedUser dispatcher;

    @BeforeEach
    void setUp() {
        workflowService = new WorkflowService(
                workflowTaskRepository,
                approvalTaskRepository,
                workflowRuleRepository,
                workflowDecisionRepository,
                auditService,
                alertService,
                diagnosticReportService
        );
        dispatcher = new AuthenticatedUser(UUID.randomUUID(), "dispatcher", RoleName.DISPATCHER, UUID.randomUUID());
    }

    @Test
    void claimTaskFansOutParallelApprovalAndReturnsApprovalChildTask() {
        UUID parentId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        WorkflowTask parent = pendingParentTask(parentId, 2);
        parent.setApprovalGroupId(groupId);

        ApprovalTask child = new ApprovalTask();
        child.setId(UUID.randomUUID());
        child.setParentTaskId(parentId);
        child.setApprovalGroupId(groupId);
        child.setApproverRole("DISPATCHER");
        child.setState(ApprovalTaskState.PENDING);
        child.setCreatedAt(Instant.now());
        child.setUpdatedAt(Instant.now());

        when(approvalTaskRepository.findByIdAndApproverRole(parentId, "DISPATCHER")).thenReturn(Optional.empty());
        when(workflowTaskRepository.findByIdAndOwnerRole(parentId, "DISPATCHER")).thenReturn(Optional.of(parent));
        when(approvalTaskRepository.findByParentTaskId(parentId)).thenReturn(List.of(), List.of(child));
        when(workflowTaskRepository.save(any(WorkflowTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(approvalTaskRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(approvalTaskRepository.save(any(ApprovalTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workflowTaskRepository.findById(parentId)).thenReturn(Optional.of(parent));

        WorkflowDtos.TaskResponse response = workflowService.claimTask(dispatcher, parentId);

        assertEquals(child.getId().toString(), response.id());
        assertEquals("ROUTE_CHANGE_APPROVAL", response.taskType());
        assertEquals("LEASED", response.state());
        verify(approvalTaskRepository).saveAll(any());
    }

    @Test
    void decideApprovalTaskRejectsSecondApprovalFromSameDispatcher() {
        UUID approvalTaskId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        ApprovalTask approval = new ApprovalTask();
        approval.setId(approvalTaskId);
        approval.setParentTaskId(parentId);
        approval.setApprovalGroupId(UUID.randomUUID());
        approval.setApproverRole("DISPATCHER");
        approval.setState(ApprovalTaskState.LEASED);
        approval.setLeaseOwnerUserId(dispatcher.userId());
        approval.setLeaseExpiresAt(Instant.now().plusSeconds(600));
        approval.setCreatedAt(Instant.now());
        approval.setUpdatedAt(Instant.now());

        when(approvalTaskRepository.findByIdAndApproverRole(approvalTaskId, "DISPATCHER")).thenReturn(Optional.of(approval));
        when(approvalTaskRepository.existsByParentTaskIdAndDecidedByUserIdAndState(parentId, dispatcher.userId(), ApprovalTaskState.APPROVED)).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class,
                () -> workflowService.decide(dispatcher, approvalTaskId, new WorkflowDtos.DecisionRequest("APPROVE", "duplicate")));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("Same dispatcher cannot provide multiple approvals for the same task", exception.getMessage());
    }

    @Test
    void batchDecideCollectsPerTaskFailuresWithoutBreakingBatch() {
        WorkflowDtos.BatchDecisionRequest request = new WorkflowDtos.BatchDecisionRequest(
                List.of("not-a-uuid"),
                "APPROVE",
                "batch"
        );

        WorkflowDtos.BatchDecisionResponse response = workflowService.batchDecide(dispatcher, request);

        assertEquals(0, response.succeeded().size());
        assertEquals(1, response.failed().size());
        assertEquals("not-a-uuid", response.failed().get(0).taskId());
    }

    @Test
    void ruleBasedBranchingCreatesFollowUpTasksWhenParentCompletes() {
        UUID parentId = UUID.randomUUID();
        WorkflowTask parent = pendingParentTask(parentId, 1);
        parent.setPayload("requiresAbnormalReview=true");

        WorkflowRule rule = new WorkflowRule();
        rule.setId(UUID.randomUUID());
        rule.setTaskType("ROUTE_CHANGE");
        rule.setTriggerField("requiresAbnormalReview");
        rule.setExpectedValue("true");
        rule.setNextTaskTypes("ABNORMAL_DATA_REVIEW");
        rule.setPriority(1);
        rule.setEnabled(true);
        rule.setCreatedAt(Instant.now());
        rule.setUpdatedAt(Instant.now());

        when(approvalTaskRepository.findByIdAndApproverRole(parentId, "DISPATCHER")).thenReturn(Optional.empty());
        when(workflowTaskRepository.findByIdAndOwnerRole(parentId, "DISPATCHER")).thenReturn(Optional.of(parent));
        when(workflowTaskRepository.save(any(WorkflowTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workflowRuleRepository.findByTaskTypeIgnoreCaseAndEnabledTrueOrderByPriorityAsc(eq("ROUTE_CHANGE"))).thenReturn(List.of(rule));

        workflowService.claimTask(dispatcher, parentId);
        workflowService.decide(dispatcher, parentId, new WorkflowDtos.DecisionRequest("APPROVE", "complete parent"));

        verify(workflowTaskRepository).saveAll(any());
    }

    private WorkflowTask pendingParentTask(UUID id, int requiredApprovals) {
        WorkflowTask task = new WorkflowTask();
        task.setId(id);
        task.setTitle("Workflow unit task");
        task.setTaskType("ROUTE_CHANGE");
        task.setState(WorkflowState.PENDING);
        task.setOwnerRole("DISPATCHER");
        task.setApprovalMode(requiredApprovals > 1 ? "ALL" : "ANY");
        task.setRequiredApprovals(requiredApprovals);
        task.setApprovalCount(0);
        task.setCurrentApprovals(0);
        task.setProgressStep(1);
        task.setProgressTotal(Math.max(requiredApprovals, 1));
        task.setEscalated(false);
        task.setResubmissionCount(0);
        task.setPayload("requiresAbnormalReview=false");
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        return task;
    }
}

