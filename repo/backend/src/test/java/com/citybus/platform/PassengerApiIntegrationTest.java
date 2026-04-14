package com.citybus.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.citybus.platform.domain.RoleName;
import com.citybus.platform.domain.User;
import com.citybus.platform.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PassengerApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void rejectsUnauthenticatedSearch() throws Exception {
        mockMvc.perform(get("/api/passenger/search/results").param("q", "101"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsSearchResultsForPassenger() throws Exception {
        String accessToken = login("passenger", "Passenger123!");
        mockMvc.perform(get("/api/passenger/search/results")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("q", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].primaryText").value("101"));
    }

    @Test
    void validatesReminderPreferences() throws Exception {
        String accessToken = login("passenger", "Passenger123!");
        mockMvc.perform(put("/api/passenger/reminders/preferences")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"leadMinutes\":0,\"dndStart\":\"2200\",\"dndEnd\":\"07:00\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createsReminderReservationForPassenger() throws Exception {
        String accessToken = login("passenger", "Passenger123!");
        JsonNode route = findByType(search(accessToken, "101").get("results"), "ROUTE");
        JsonNode stop = findByType(search(accessToken, "central").get("results"), "STOP");

        mockMvc.perform(post("/api/passenger/reminders/subscriptions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "routeId":"%s",
                                  "stopId":"%s",
                                  "reservationName":"Morning trip",
                                  "scheduledArrivalAt":"2030-01-01T08:30:00Z"
                                }
                                """.formatted(route.get("id").asText(), stop.get("id").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationName").value("Morning trip"))
                .andExpect(jsonPath("$.canceled").value(false));
    }

    @Test
    void rejectsReservationForStopOutsideRoute() throws Exception {
        String accessToken = login("passenger", "Passenger123!");
        JsonNode route = findByType(search(accessToken, "101").get("results"), "ROUTE");
        JsonNode stop = findByType(search(accessToken, "old town").get("results"), "STOP");

        mockMvc.perform(post("/api/passenger/reminders/subscriptions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "routeId":"%s",
                                  "stopId":"%s",
                                  "reservationName":"Invalid route stop pair",
                                  "scheduledArrivalAt":"2030-01-01T08:30:00Z"
                                }
                                """.formatted(route.get("id").asText(), stop.get("id").asText())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void returnsNotFoundForMissingMessage() throws Exception {
        String accessToken = login("passenger", "Passenger123!");
        mockMvc.perform(post("/api/passenger/messages/00000000-0000-0000-0000-000000000000/read")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsCrossUserSubscriptionMutation() throws Exception {
        ensurePassengerUser("passenger2", "Passenger234!");
        String passengerToken = login("passenger", "Passenger123!");
        String passenger2Token = login("passenger2", "Passenger234!");

        JsonNode route = findByType(search(passengerToken, "101").get("results"), "ROUTE");
        JsonNode stop = findByType(search(passengerToken, "central").get("results"), "STOP");
        String createResponse = mockMvc.perform(post("/api/passenger/reminders/subscriptions")
                        .header("Authorization", "Bearer " + passengerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "routeId":"%s",
                                  "stopId":"%s",
                                  "reservationName":"Cross user protection check",
                                  "scheduledArrivalAt":"2030-01-01T09:00:00Z"
                                }
                                """.formatted(route.get("id").asText(), stop.get("id").asText())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String subscriptionId = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(post("/api/passenger/reminders/subscriptions/" + subscriptionId + "/cancel")
                        .header("Authorization", "Bearer " + passenger2Token))
                .andExpect(status().isNotFound());
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

    private JsonNode search(String accessToken, String query) throws Exception {
        String body = mockMvc.perform(get("/api/passenger/search/results")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("q", query))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode findByType(JsonNode results, String type) {
        for (JsonNode result : results) {
            if (type.equals(result.get("type").asText())) {
                return result;
            }
        }
        throw new IllegalStateException("Expected result type " + type);
    }

    private void ensurePassengerUser(String username, String password) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName("Passenger Two");
        user.setRoleName(RoleName.PASSENGER);
        user.setTemporaryPassword(false);
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }
}
