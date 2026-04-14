package com.citybus.platform.api;

import com.citybus.platform.api.dto.PassengerDtos;
import com.citybus.platform.application.PassengerService;
import com.citybus.platform.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/passenger")
@PreAuthorize("hasRole('PASSENGER')")
public class PassengerController {
    private final PassengerService passengerService;

    public PassengerController(PassengerService passengerService) {
        this.passengerService = passengerService;
    }

    @GetMapping("/search/suggestions")
    public PassengerDtos.SearchResponse suggestions(@RequestParam String q) {
        return passengerService.search(q);
    }

    @GetMapping("/search/results")
    public PassengerDtos.SearchResponse results(@RequestParam String q) {
        return passengerService.search(q);
    }

    @GetMapping("/reminders/preferences")
    public PassengerDtos.ReminderPreferenceResponse preferences(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return passengerService.getPreferences(currentUser);
    }

    @PutMapping("/reminders/preferences")
    public PassengerDtos.ReminderPreferenceResponse updatePreferences(@AuthenticationPrincipal AuthenticatedUser currentUser, @Valid @RequestBody PassengerDtos.ReminderPreferenceRequest request) {
        return passengerService.updatePreferences(currentUser, request);
    }

    @GetMapping("/reminders/subscriptions")
    public List<PassengerDtos.ReminderSubscriptionResponse> subscriptions(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return passengerService.subscriptions(currentUser);
    }

    @PostMapping("/reminders/subscriptions")
    public PassengerDtos.ReminderSubscriptionResponse createSubscription(@AuthenticationPrincipal AuthenticatedUser currentUser, @Valid @RequestBody PassengerDtos.ReminderSubscriptionRequest request) {
        return passengerService.createSubscription(currentUser, request);
    }

    @PostMapping("/reminders/subscriptions/{id}/check-in")
    public PassengerDtos.ReminderSubscriptionResponse checkIn(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable UUID id) {
        return passengerService.checkIn(currentUser, id);
    }

    @PostMapping("/reminders/subscriptions/{id}/cancel")
    public PassengerDtos.ReminderSubscriptionResponse cancel(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable UUID id) {
        return passengerService.cancelSubscription(currentUser, id);
    }

    @GetMapping("/messages")
    public List<PassengerDtos.MessageResponse> messages(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return passengerService.messages(currentUser);
    }

    @PostMapping("/messages/{id}/read")
    public Map<String, String> markRead(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable UUID id) {
        passengerService.markRead(currentUser, id);
        return Map.of("status", "ok");
    }
}
