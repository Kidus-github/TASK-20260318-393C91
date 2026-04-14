package com.citybus.platform.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;

public final class PassengerDtos {
    private PassengerDtos() {
    }

    public record SearchSuggestion(String id, String label, String type, int score) {}
    public record SearchResult(String id, String primaryText, String secondaryText, String type, int score) {}
    public record SearchResponse(List<SearchSuggestion> suggestions, List<SearchResult> results) {}

    public record ReminderPreferenceResponse(boolean enabled, int leadMinutes, String dndStart, String dndEnd) {}

    public record ReminderPreferenceRequest(
            boolean enabled,
            @Min(1) @Max(120) int leadMinutes,
            @Pattern(regexp = "^\\d{2}:\\d{2}$") String dndStart,
            @Pattern(regexp = "^\\d{2}:\\d{2}$") String dndEnd
    ) {}

    public record ReminderSubscriptionRequest(
            @NotBlank String routeId,
            @NotBlank String stopId,
            @NotBlank String reservationName,
            @NotBlank String scheduledArrivalAt
    ) {}

    public record ReminderSubscriptionResponse(
            String id,
            String routeId,
            String stopId,
            String reservationName,
            Instant scheduledArrivalAt,
            Instant reminderAt,
            Instant checkedInAt,
            boolean canceled,
            boolean reminderSent,
            boolean missedCheckInSent
    ) {}

    public record MessageResponse(String id, String type, String title, String content, boolean read, Instant createdAt) {}

    public record SearchQuery(
            @NotBlank String q
    ) {}
}
