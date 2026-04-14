package com.citybus.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.citybus.platform.domain.RoleName;
import com.citybus.platform.domain.WorkflowState;
import com.citybus.platform.domain.WorkflowTask;
import com.citybus.platform.domain.User;
import com.citybus.platform.infrastructure.persistence.ApprovalTaskRepository;
import com.citybus.platform.infrastructure.persistence.UserRepository;
import com.citybus.platform.infrastructure.persistence.WorkflowTaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminDispatcherIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkflowTaskRepository workflowTaskRepository;
    @Autowired
    private ApprovalTaskRepository approvalTaskRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Test
    void forbidsPassengerFromAdminEndpoints() throws Exception {
        String accessToken = login("passenger", "Passenger123!");
        mockMvc.perform(get("/api/admin/templates").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void dispatcherCanListTasks() throws Exception {
        String accessToken = login("dispatcher", "Dispatch123!");
        mockMvc.perform(get("/api/dispatcher/tasks").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists());
    }

    @Test
    void validationErrorOnBatchDecisionWithoutTaskIds() throws Exception {
        String accessToken = login("dispatcher", "Dispatch123!");
        mockMvc.perform(post("/api/dispatcher/tasks/batch-decision")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskIds\":[],\"decision\":\"APPROVE\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void dispatcherCanReturnAndResubmitTask() throws Exception {
        String accessToken = login("dispatcher", "Dispatch123!");
        String taskId = mockMvc.perform(get("/api/dispatcher/tasks").header("Authorization", "Bearer " + accessToken))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = objectMapper.readTree(taskId).get(0).get("id").asText();

        mockMvc.perform(post("/api/dispatcher/tasks/" + id + "/claim").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/dispatcher/tasks/" + id + "/decision")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"RETURN\",\"comment\":\"Need revision\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RETURNED"));
        mockMvc.perform(post("/api/dispatcher/tasks/" + id + "/resubmit")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"RESUBMIT\",\"comment\":\"Updated and resubmitted\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resubmissionCount").value(1));
    }

    @Test
    void adminResetRevokesExistingUserSessions() throws Exception {
        String passengerToken = login("passenger", "Passenger123!");
        String adminToken = login("admin", "Admin123!");

        String body = mockMvc.perform(post("/api/admin/users/reset-password")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"passenger\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestToken").value(org.hamcrest.Matchers.startsWith("RST-")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + passengerToken))
                .andExpect(status().isUnauthorized());

        String requestToken = objectMapper.readTree(body).get("requestToken").asText();
        mockMvc.perform(post("/api/admin/users/reset-password/reveal")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestToken\":\"" + requestToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").exists());
    }

    @Test
    void adminCanUpdateCleaningRule() throws Exception {
        String adminToken = login("admin", "Admin123!");
        String rulesBody = mockMvc.perform(get("/api/admin/cleaning-rules").header("Authorization", "Bearer " + adminToken))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = objectMapper.readTree(rulesBody).get(0).get("id").asText();

        mockMvc.perform(put("/api/admin/cleaning-rules/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pattern\":\"\\\\s+\",\"replacementValue\":\" \",\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void adminCanReadDiagnosticReportsEndpoints() throws Exception {
        String adminToken = login("admin", "Admin123!");
        String listBody = mockMvc.perform(get("/api/admin/reports").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode reports = objectMapper.readTree(listBody);
        if (reports.isArray() && reports.size() > 0) {
            mockMvc.perform(get("/api/admin/reports/" + reports.get(0).get("id").asText()).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void preventsSameDispatcherFromCompletingMultiApprovalTaskAlone() throws Exception {
        WorkflowTask parent = new WorkflowTask();
        parent.setId(UUID.randomUUID());
        parent.setTitle("Single dispatcher guard");
        parent.setTaskType("ROUTE_CHANGE");
        parent.setState(WorkflowState.PENDING);
        parent.setOwnerRole("DISPATCHER");
        parent.setApprovalMode("ALL");
        parent.setRequiredApprovals(2);
        parent.setApprovalCount(0);
        parent.setCurrentApprovals(0);
        parent.setProgressStep(1);
        parent.setProgressTotal(2);
        parent.setEscalated(false);
        parent.setResubmissionCount(0);
        parent.setPayload("requiresAbnormalReview=false");
        parent.setCreatedAt(Instant.now());
        parent.setUpdatedAt(Instant.now());
        workflowTaskRepository.saveAndFlush(parent);

        String accessToken = login("dispatcher", "Dispatch123!");
        mockMvc.perform(post("/api/dispatcher/tasks/" + parent.getId() + "/claim")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        List<String> approvalTaskIds = approvalTaskRepository.findByParentTaskId(parent.getId()).stream()
                .map(task -> task.getId().toString())
                .toList();
        org.junit.jupiter.api.Assertions.assertEquals(2, approvalTaskIds.size());
        String firstTaskId = approvalTaskIds.get(0);
        String secondTaskId = approvalTaskIds.get(1);
        org.junit.jupiter.api.Assertions.assertNotNull(firstTaskId);
        org.junit.jupiter.api.Assertions.assertNotNull(secondTaskId);

        mockMvc.perform(post("/api/dispatcher/tasks/" + firstTaskId + "/claim").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/dispatcher/tasks/" + firstTaskId + "/decision")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"comment\":\"First approval\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/dispatcher/tasks/" + secondTaskId + "/claim").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/dispatcher/tasks/" + secondTaskId + "/decision")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"comment\":\"Second approval\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("cannot provide multiple approvals")));
    }

    @Test
    void createsRealFollowupTaskWhenAbnormalReviewBranchConditionMatches() throws Exception {
        WorkflowTask sourceTask = new WorkflowTask();
        sourceTask.setId(UUID.randomUUID());
        sourceTask.setTitle("Branch test task");
        sourceTask.setTaskType("ROUTE_CHANGE");
        sourceTask.setState(WorkflowState.PENDING);
        sourceTask.setOwnerRole("DISPATCHER");
        sourceTask.setApprovalMode("ALL");
        sourceTask.setRequiredApprovals(1);
        sourceTask.setApprovalCount(0);
        sourceTask.setProgressStep(1);
        sourceTask.setProgressTotal(1);
        sourceTask.setEscalated(false);
        sourceTask.setResubmissionCount(0);
        sourceTask.setPayload("requiresAbnormalReview=true");
        sourceTask.setCreatedAt(Instant.now());
        sourceTask.setUpdatedAt(Instant.now());
        workflowTaskRepository.save(sourceTask);

        String accessToken = login("dispatcher", "Dispatch123!");
        mockMvc.perform(post("/api/dispatcher/tasks/" + sourceTask.getId() + "/claim").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/dispatcher/tasks/" + sourceTask.getId() + "/decision")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"comment\":\"Approve and branch\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"));

        List<WorkflowTask> followUps = workflowTaskRepository.findAll().stream()
                .filter(task -> "ABNORMAL_DATA_REVIEW".equals(task.getTaskType()))
                .filter(task -> task.getPayload() != null && task.getPayload().contains("parentTaskId=" + sourceTask.getId()))
                .toList();
        org.junit.jupiter.api.Assertions.assertEquals(1, followUps.size());
        org.junit.jupiter.api.Assertions.assertEquals(WorkflowState.PENDING, followUps.get(0).getState());
    }

    @Test
    void parsingTemplateSaveCreatesNewVersionInsteadOfOverwritingExisting() throws Exception {
        String adminToken = login("admin", "Admin123!");
        String templateName = "history-template-" + UUID.randomUUID();

        String firstBody = objectMapper.writeValueAsString(Map.of(
                "templateName", templateName,
                "templateType", "JSON",
                "semanticVersion", "1.0.0",
                "body", "{\"fields\":{\"stopName\":\"stop.name\"}}",
                "active", false,
                "revision", 0
        ));
        mockMvc.perform(post("/api/admin/parsing/templates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(1));

        String secondBody = objectMapper.writeValueAsString(Map.of(
                "templateName", templateName,
                "templateType", "JSON",
                "semanticVersion", "1.0.1",
                "body", "{\"fields\":{\"stopName\":\"stop.displayName\"}}",
                "active", false,
                "revision", 1
        ));
        mockMvc.perform(post("/api/admin/parsing/templates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(2));

        JsonNode templates = objectMapper.readTree(
                mockMvc.perform(get("/api/admin/parsing/templates").header("Authorization", "Bearer " + adminToken))
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        );
        List<JsonNode> versions = new java.util.ArrayList<>();
        for (JsonNode item : templates) {
            if (templateName.equals(item.get("templateName").asText())) {
                versions.add(item);
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(2, versions.size());
        org.junit.jupiter.api.Assertions.assertNotEquals(versions.get(0).get("id").asText(), versions.get(1).get("id").asText());
    }

    @Test
    void parallelApprovalsCanBeCompletedByDifferentDispatchers() throws Exception {
        ensureDispatcherUser("dispatcher2", "Dispatch234!");
        WorkflowTask parent = new WorkflowTask();
        parent.setId(UUID.randomUUID());
        parent.setTitle("Parallel approval test");
        parent.setTaskType("ROUTE_CHANGE");
        parent.setState(WorkflowState.PENDING);
        parent.setOwnerRole("DISPATCHER");
        parent.setApprovalMode("ALL");
        parent.setRequiredApprovals(2);
        parent.setApprovalCount(0);
        parent.setCurrentApprovals(0);
        parent.setProgressStep(1);
        parent.setProgressTotal(2);
        parent.setEscalated(false);
        parent.setResubmissionCount(0);
        parent.setPayload("requiresAbnormalReview=false");
        parent.setCreatedAt(Instant.now());
        parent.setUpdatedAt(Instant.now());
        workflowTaskRepository.saveAndFlush(parent);

        String dispatcher1 = login("dispatcher", "Dispatch123!");
        String dispatcher2 = login("dispatcher2", "Dispatch234!");
        mockMvc.perform(post("/api/dispatcher/tasks/" + parent.getId() + "/claim")
                        .header("Authorization", "Bearer " + dispatcher1))
                .andExpect(status().isOk());
        List<String> approvalTaskIds = approvalTaskRepository.findByParentTaskId(parent.getId()).stream()
                .map(task -> task.getId().toString())
                .toList();
        org.junit.jupiter.api.Assertions.assertEquals(2, approvalTaskIds.size());

        mockMvc.perform(post("/api/dispatcher/tasks/" + approvalTaskIds.get(0) + "/claim").header("Authorization", "Bearer " + dispatcher1))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/dispatcher/tasks/" + approvalTaskIds.get(0) + "/decision")
                        .header("Authorization", "Bearer " + dispatcher1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"comment\":\"Dispatcher one\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/dispatcher/tasks/" + approvalTaskIds.get(1) + "/claim").header("Authorization", "Bearer " + dispatcher2))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/dispatcher/tasks/" + approvalTaskIds.get(1) + "/decision")
                        .header("Authorization", "Bearer " + dispatcher2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"comment\":\"Dispatcher two\"}"))
                .andExpect(status().isOk());

        WorkflowTask updated = workflowTaskRepository.findById(parent.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(WorkflowState.COMPLETED, updated.getState());
        org.junit.jupiter.api.Assertions.assertEquals(2, updated.getCurrentApprovals());
    }

    @Test
    void configurableWorkflowRuleCanSpawnMultipleFollowUpTasks() throws Exception {
        WorkflowTask parent = new WorkflowTask();
        parent.setId(UUID.randomUUID());
        parent.setTitle("Reminder rule branching");
        parent.setTaskType("REMINDER_RULE_CONFIG");
        parent.setState(WorkflowState.PENDING);
        parent.setOwnerRole("DISPATCHER");
        parent.setApprovalMode("ANY");
        parent.setRequiredApprovals(1);
        parent.setApprovalCount(0);
        parent.setCurrentApprovals(0);
        parent.setProgressStep(1);
        parent.setProgressTotal(1);
        parent.setEscalated(false);
        parent.setResubmissionCount(0);
        parent.setPayload("requestedValue=15");
        parent.setCreatedAt(Instant.now());
        parent.setUpdatedAt(Instant.now());
        workflowTaskRepository.save(parent);

        String token = login("dispatcher", "Dispatch123!");
        mockMvc.perform(post("/api/dispatcher/tasks/" + parent.getId() + "/claim").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/dispatcher/tasks/" + parent.getId() + "/decision")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"comment\":\"Apply reminder rule\"}"))
                .andExpect(status().isOk());

        List<WorkflowTask> followUps = workflowTaskRepository.findAll().stream()
                .filter(task -> parent.getId().equals(task.getParentTaskId()))
                .toList();
        org.junit.jupiter.api.Assertions.assertEquals(2, followUps.size());
    }

    private String login(String username, String password) throws Exception {
        String payload = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(body);
        return jsonNode.get("accessToken").asText();
    }

    private void ensureDispatcherUser(String username, String password) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName("Dispatcher Two");
        user.setRoleName(RoleName.DISPATCHER);
        user.setTemporaryPassword(false);
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }
}
