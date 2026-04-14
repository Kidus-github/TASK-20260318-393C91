package tests.api_tests;

import com.citybus.platform.CityBusApplication;
import com.citybus.platform.domain.WorkflowState;
import com.citybus.platform.domain.WorkflowTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.citybus.platform.infrastructure.persistence.WorkflowTaskRepository;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CityBusApplication.class)
@AutoConfigureMockMvc
class RoleAndBusinessApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkflowTaskRepository workflowTaskRepository;

    @Test
    void enforcesRoleBoundariesBetweenPassengerDispatcherAndAdmin() throws Exception {
        String passengerToken = login("passenger", "Passenger123!");

        mockMvc.perform(get("/api/admin/templates").header("Authorization", "Bearer " + passengerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/dispatcher/tasks").header("Authorization", "Bearer " + passengerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void passengerSearchAndReminderValidationAreCovered() throws Exception {
        String passengerToken = login("passenger", "Passenger123!");

        mockMvc.perform(get("/api/passenger/search/results")
                        .param("q", "101")
                        .header("Authorization", "Bearer " + passengerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions").isArray())
                .andExpect(jsonPath("$.results").isArray());

        mockMvc.perform(put("/api/passenger/reminders/preferences")
                        .header("Authorization", "Bearer " + passengerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"leadMinutes\":0,\"dndStart\":\"22:00\",\"dndEnd\":\"07:00\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void dispatcherCanClaimAndCompleteTaskAndBatchValidationFailsWhenEmpty() throws Exception {
        WorkflowTask task = new WorkflowTask();
        task.setId(UUID.randomUUID());
        task.setTitle("API test route change");
        task.setTaskType("ROUTE_CHANGE");
        task.setState(WorkflowState.PENDING);
        task.setOwnerRole("DISPATCHER");
        task.setApprovalMode("ANY");
        task.setRequiredApprovals(1);
        task.setApprovalCount(0);
        task.setCurrentApprovals(0);
        task.setProgressStep(1);
        task.setProgressTotal(1);
        task.setEscalated(false);
        task.setResubmissionCount(0);
        task.setPayload("requiresAbnormalReview=false");
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        workflowTaskRepository.save(task);

        String dispatcherToken = login("dispatcher", "Dispatch123!");

        mockMvc.perform(post("/api/dispatcher/tasks/" + task.getId() + "/claim")
                        .header("Authorization", "Bearer " + dispatcherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("LEASED"));

        mockMvc.perform(post("/api/dispatcher/tasks/" + task.getId() + "/decision")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"comment\":\"API integration approval\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"));

        mockMvc.perform(post("/api/dispatcher/tasks/batch-decision")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskIds\":[],\"decision\":\"APPROVE\",\"comment\":\"none\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void adminCanReadCoreAdministrativeEndpoints() throws Exception {
        String adminToken = login("admin", "Admin123!");

        mockMvc.perform(get("/api/admin/templates").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(get("/api/admin/reports").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("accessToken").asText();
    }
}
