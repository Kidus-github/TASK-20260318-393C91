package com.citybus.platform.application;

import com.citybus.platform.api.dto.PassengerDtos;
import com.citybus.platform.domain.Message;
import com.citybus.platform.domain.NotificationTemplate;
import com.citybus.platform.domain.QueueStatus;
import com.citybus.platform.domain.QueuedMessage;
import com.citybus.platform.domain.ReminderPreference;
import com.citybus.platform.domain.ReminderSubscription;
import com.citybus.platform.domain.Route;
import com.citybus.platform.domain.SearchWeightConfig;
import com.citybus.platform.domain.SensitivityLevel;
import com.citybus.platform.domain.Stop;
import com.citybus.platform.infrastructure.observability.TraceIdFilter;
import com.citybus.platform.infrastructure.persistence.MessageRepository;
import com.citybus.platform.infrastructure.persistence.NotificationTemplateRepository;
import com.citybus.platform.infrastructure.persistence.QueuedMessageRepository;
import com.citybus.platform.infrastructure.persistence.ReminderPreferenceRepository;
import com.citybus.platform.infrastructure.persistence.ReminderSubscriptionRepository;
import com.citybus.platform.infrastructure.persistence.RouteRepository;
import com.citybus.platform.infrastructure.persistence.RouteStopRepository;
import com.citybus.platform.infrastructure.persistence.SearchWeightConfigRepository;
import com.citybus.platform.infrastructure.persistence.StopRepository;
import com.citybus.platform.infrastructure.security.AuthenticatedUser;
import org.slf4j.MDC;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class PassengerService {
    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;
    private final ReminderPreferenceRepository reminderPreferenceRepository;
    private final ReminderSubscriptionRepository reminderSubscriptionRepository;
    private final RouteStopRepository routeStopRepository;
    private final SearchWeightConfigRepository searchWeightConfigRepository;
    private final NotificationTemplateRepository notificationTemplateRepository;
    private final MessageRepository messageRepository;
    private final QueuedMessageRepository queuedMessageRepository;
    private final AuditService auditService;
    private final AppProperties appProperties;

    public PassengerService(
            RouteRepository routeRepository,
            StopRepository stopRepository,
            ReminderPreferenceRepository reminderPreferenceRepository,
            ReminderSubscriptionRepository reminderSubscriptionRepository,
            RouteStopRepository routeStopRepository,
            SearchWeightConfigRepository searchWeightConfigRepository,
            NotificationTemplateRepository notificationTemplateRepository,
            MessageRepository messageRepository,
            QueuedMessageRepository queuedMessageRepository,
            AuditService auditService,
            AppProperties appProperties
    ) {
        this.routeRepository = routeRepository;
        this.stopRepository = stopRepository;
        this.reminderPreferenceRepository = reminderPreferenceRepository;
        this.reminderSubscriptionRepository = reminderSubscriptionRepository;
        this.routeStopRepository = routeStopRepository;
        this.searchWeightConfigRepository = searchWeightConfigRepository;
        this.notificationTemplateRepository = notificationTemplateRepository;
        this.messageRepository = messageRepository;
        this.queuedMessageRepository = queuedMessageRepository;
        this.auditService = auditService;
        this.appProperties = appProperties;
    }

    @Cacheable("search")
    @Transactional(readOnly = true)
    public PassengerDtos.SearchResponse search(String rawQuery) {
        String query = rawQuery.toLowerCase(Locale.ROOT).trim();
        SearchWeightConfig weights = searchWeights();
        List<Route> routes = routeRepository.search(query);
        List<Stop> stops = stopRepository.search(query);
        Map<String, PassengerDtos.SearchResult> deduped = new LinkedHashMap<>();
        List<PassengerDtos.SearchSuggestion> suggestions = new ArrayList<>();

        for (Route route : routes) {
            int score = scoreRoute(route, query, weights);
            PassengerDtos.SearchResult item = new PassengerDtos.SearchResult(route.getId().toString(), route.getRouteNumber(), route.getRouteName(), "ROUTE", score);
            deduped.putIfAbsent("ROUTE:" + route.getRouteNumber(), item);
            suggestions.add(new PassengerDtos.SearchSuggestion(route.getId().toString(), route.getRouteNumber() + " - " + route.getRouteName(), "ROUTE", score));
        }
        for (Stop stop : stops) {
            int score = scoreStop(stop, query, weights);
            PassengerDtos.SearchResult item = new PassengerDtos.SearchResult(stop.getId().toString(), stop.getStopName(), stop.getAddress(), "STOP", score);
            deduped.putIfAbsent("STOP:" + stop.getStopName().toLowerCase(Locale.ROOT), item);
            suggestions.add(new PassengerDtos.SearchSuggestion(stop.getId().toString(), stop.getStopName(), "STOP", score));
        }

        List<PassengerDtos.SearchResult> sortedResults = deduped.values().stream()
                .sorted(Comparator.comparingInt(PassengerDtos.SearchResult::score).reversed())
                .toList();
        List<PassengerDtos.SearchSuggestion> sortedSuggestions = suggestions.stream()
                .sorted(Comparator.comparingInt(PassengerDtos.SearchSuggestion::score).reversed())
                .limit(10)
                .toList();
        return new PassengerDtos.SearchResponse(sortedSuggestions, sortedResults);
    }

    @Transactional(readOnly = true)
    public PassengerDtos.ReminderPreferenceResponse getPreferences(AuthenticatedUser currentUser) {
        ReminderPreference preference = reminderPreferenceRepository.findById(currentUser.userId()).orElseGet(() -> defaultPreference(currentUser.userId()));
        return new PassengerDtos.ReminderPreferenceResponse(preference.isEnabled(), preference.getLeadMinutes(), preference.getDndStart(), preference.getDndEnd());
    }

    @Transactional
    public PassengerDtos.ReminderPreferenceResponse updatePreferences(AuthenticatedUser currentUser, PassengerDtos.ReminderPreferenceRequest request) {
        validateDnd(request.dndStart(), request.dndEnd());
        ReminderPreference preference = reminderPreferenceRepository.findById(currentUser.userId()).orElseGet(() -> defaultPreference(currentUser.userId()));
        preference.setEnabled(request.enabled());
        preference.setLeadMinutes(request.leadMinutes());
        preference.setDndStart(request.dndStart());
        preference.setDndEnd(request.dndEnd());
        preference.setUpdatedAt(Instant.now());
        reminderPreferenceRepository.save(preference);

        queueMessage(currentUser.userId(), "REMINDER_PREF_UPDATED", Map.of());
        auditService.log(currentUser.userId(), "REMINDER_PREFERENCE_UPDATED", "USER", currentUser.userId().toString(), "Updated reminder preferences");
        return new PassengerDtos.ReminderPreferenceResponse(preference.isEnabled(), preference.getLeadMinutes(), preference.getDndStart(), preference.getDndEnd());
    }

    @Transactional(readOnly = true)
    public List<PassengerDtos.ReminderSubscriptionResponse> subscriptions(AuthenticatedUser currentUser) {
        return reminderSubscriptionRepository.findByUserIdOrderByScheduledArrivalAtAsc(currentUser.userId()).stream()
                .map(this::toSubscriptionResponse)
                .toList();
    }

    @Transactional
    public PassengerDtos.ReminderSubscriptionResponse createSubscription(AuthenticatedUser currentUser, PassengerDtos.ReminderSubscriptionRequest request) {
        Route route = routeRepository.findById(parseUuid(request.routeId(), "Route"))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Route not found"));
        Stop stop = stopRepository.findById(parseUuid(request.stopId(), "Stop"))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Stop not found"));
        if (!routeStopRepository.existsLink(route.getId(), stop.getId())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Selected stop does not belong to the selected route");
        }
        ReminderPreference preference = reminderPreferenceRepository.findById(currentUser.userId()).orElseGet(() -> defaultPreference(currentUser.userId()));
        if (!preference.isEnabled()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Arrival reminders are disabled for this user");
        }

        Instant scheduledArrivalAt = Instant.parse(request.scheduledArrivalAt());
        if (!scheduledArrivalAt.isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Scheduled arrival must be in the future");
        }
        ReminderSubscription subscription = new ReminderSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setUserId(currentUser.userId());
        subscription.setRouteId(route.getId());
        subscription.setStopId(stop.getId());
        subscription.setReservationName(request.reservationName());
        subscription.setScheduledArrivalAt(scheduledArrivalAt);
        subscription.setReminderAt(scheduledArrivalAt.minusSeconds((long) preference.getLeadMinutes() * 60));
        subscription.setCreatedAt(Instant.now());
        subscription.setUpdatedAt(Instant.now());
        reminderSubscriptionRepository.save(subscription);

        queueMessage(currentUser.userId(), "RESERVATION_CONFIRMED", Map.of(
                "reservationName", request.reservationName(),
                "routeNumber", route.getRouteNumber(),
                "stopName", stop.getStopName()
        ));
        auditService.log(currentUser.userId(), "REMINDER_SUBSCRIPTION_CREATED", "REMINDER_SUBSCRIPTION", subscription.getId().toString(), request.reservationName());
        return toSubscriptionResponse(subscription);
    }

    @Transactional
    public PassengerDtos.ReminderSubscriptionResponse cancelSubscription(AuthenticatedUser currentUser, UUID id) {
        ReminderSubscription subscription = reminderSubscriptionRepository.findByIdAndUserId(id, currentUser.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Reminder subscription not found"));
        subscription.setCanceled(true);
        subscription.setUpdatedAt(Instant.now());
        reminderSubscriptionRepository.save(subscription);
        queueMessage(currentUser.userId(), "REMINDER_CANCELLED", Map.of(
                "reservationName", subscription.getReservationName()
        ));
        return toSubscriptionResponse(subscription);
    }

    @Transactional
    public PassengerDtos.ReminderSubscriptionResponse checkIn(AuthenticatedUser currentUser, UUID id) {
        ReminderSubscription subscription = reminderSubscriptionRepository.findByIdAndUserId(id, currentUser.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Reminder subscription not found"));
        subscription.setCheckedInAt(Instant.now());
        subscription.setUpdatedAt(Instant.now());
        reminderSubscriptionRepository.save(subscription);
        queueMessage(currentUser.userId(), "CHECK_IN_SUCCESS", Map.of(
                "reservationName", subscription.getReservationName()
        ));
        return toSubscriptionResponse(subscription);
    }

    @Transactional(readOnly = true)
    public List<PassengerDtos.MessageResponse> messages(AuthenticatedUser currentUser) {
        return messageRepository.findByUserIdOrderByCreatedAtDesc(currentUser.userId()).stream()
                .map(message -> new PassengerDtos.MessageResponse(
                        message.getId().toString(),
                        message.getType(),
                        message.getTitle(),
                        mask(message),
                        message.isRead(),
                        message.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void markRead(AuthenticatedUser currentUser, UUID id) {
        Message message = messageRepository.findByIdAndUserId(id, currentUser.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Message not found"));
        message.setRead(true);
        messageRepository.save(message);
    }

    @Transactional
    public void processReminderSchedules() {
        Instant now = Instant.now();
        reminderSubscriptionRepository.findByReminderSentFalseAndCanceledFalseAndReminderAtBefore(now)
                .forEach(subscription -> {
                    ReminderPreference preference = reminderPreferenceRepository.findById(subscription.getUserId()).orElseGet(() -> defaultPreference(subscription.getUserId()));
                    if (!preference.isEnabled()) {
                        return;
                    }
                    Instant scheduledAt = applyDndWindow(now, preference);
                    queueMessage(subscription.getUserId(), "UPCOMING_REMINDER", Map.of(
                            "reservationName", subscription.getReservationName()
                    ), scheduledAt, SensitivityLevel.LOW);
                    subscription.setReminderSent(true);
                    subscription.setUpdatedAt(now);
                    reminderSubscriptionRepository.save(subscription);
                });

        reminderSubscriptionRepository.findByMissedCheckInSentFalseAndCanceledFalseAndCheckedInAtIsNullAndScheduledArrivalAtBefore(now.minusSeconds(300))
                .forEach(subscription -> {
                    queueMessage(subscription.getUserId(), "MISSED_CHECK_IN", Map.of(
                            "reservationName", subscription.getReservationName()
                    ), now, SensitivityLevel.INTERNAL);
                    subscription.setMissedCheckInSent(true);
                    subscription.setUpdatedAt(now);
                    reminderSubscriptionRepository.save(subscription);
                });
    }

    private ReminderPreference defaultPreference(UUID userId) {
        ReminderPreference preference = new ReminderPreference();
        preference.setUserId(userId);
        preference.setEnabled(true);
        preference.setLeadMinutes(appProperties.reminders().defaultLeadMinutes());
        preference.setDndStart("22:00");
        preference.setDndEnd("07:00");
        preference.setUpdatedAt(Instant.now());
        return preference;
    }

    private void validateDnd(String start, String end) {
        LocalTime.parse(start);
        LocalTime.parse(end);
    }

    private String mask(Message message) {
        return switch (message.getSensitivityLevel()) {
            case HIGH -> "[Sensitive content hidden]";
            case INTERNAL -> partiallyMask(message.getContent());
            case LOW -> message.getContent();
        };
    }

    private void queueMessage(UUID userId, String type, Map<String, String> variables) {
        queueMessage(userId, type, variables, Instant.now(), SensitivityLevel.LOW);
    }

    private void queueMessage(UUID userId, String type, Map<String, String> variables, Instant scheduledAt, SensitivityLevel sensitivityLevel) {
        NotificationTemplate template = notificationTemplateRepository.findByTemplateKey(type).orElse(null);
        String title = renderTemplate(template != null ? template.getTitleTemplate() : defaultTitle(type), variables);
        String content = renderTemplate(template != null ? template.getContentTemplate() : defaultContent(type), variables);
        QueuedMessage queuedMessage = new QueuedMessage();
        queuedMessage.setId(UUID.randomUUID());
        queuedMessage.setUserId(userId);
        queuedMessage.setMessageType(type);
        queuedMessage.setPayload(serializeVariables(variables));
        queuedMessage.setTitle(title);
        queuedMessage.setContent(content);
        queuedMessage.setScheduledAt(scheduledAt);
        queuedMessage.setRetryCount(0);
        queuedMessage.setStatus(QueueStatus.PENDING);
        queuedMessage.setIdempotencyKey(type + "-" + userId + "-" + UUID.randomUUID());
        queuedMessage.setSensitivityLevel(sensitivityLevel);
        queuedMessage.setTraceId(MDC.get(TraceIdFilter.TRACE_ID));
        queuedMessageRepository.save(queuedMessage);
    }

    private String renderTemplate(String template, Map<String, String> variables) {
        String rendered = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return rendered;
    }

    private String serializeVariables(Map<String, String> variables) {
        return variables.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + ";" + right)
                .orElse("{}");
    }

    private String defaultTitle(String type) {
        return switch (type) {
            case "REMINDER_PREF_UPDATED" -> "Reminder settings updated";
            case "RESERVATION_CONFIRMED" -> "Reservation confirmed";
            case "UPCOMING_REMINDER" -> "Upcoming arrival reminder";
            case "MISSED_CHECK_IN" -> "Missed check-in";
            case "REMINDER_CANCELLED" -> "Reminder cancelled";
            case "CHECK_IN_SUCCESS" -> "Check-in recorded";
            default -> type;
        };
    }

    private String defaultContent(String type) {
        return switch (type) {
            case "REMINDER_PREF_UPDATED" -> "Your arrival reminder settings were saved.";
            case "RESERVATION_CONFIRMED" -> "Your reservation reminder was created successfully.";
            case "UPCOMING_REMINDER" -> "Your reserved bus is approaching.";
            case "MISSED_CHECK_IN" -> "No check-in was recorded in the expected window.";
            case "REMINDER_CANCELLED" -> "The reminder reservation was cancelled.";
            case "CHECK_IN_SUCCESS" -> "Your check-in was recorded.";
            default -> type;
        };
    }

    private Instant applyDndWindow(Instant now, ReminderPreference preference) {
        if (!preference.isEnabled()) {
            return now;
        }
        LocalTime current = LocalDateTime.ofInstant(now, ZoneId.systemDefault()).toLocalTime();
        LocalTime start = LocalTime.parse(preference.getDndStart());
        LocalTime end = LocalTime.parse(preference.getDndEnd());
        if (!isWithinDnd(current, start, end)) {
            return now;
        }
        LocalDate currentDate = LocalDateTime.ofInstant(now, ZoneId.systemDefault()).toLocalDate();
        LocalDateTime endDateTime = LocalDateTime.of(currentDate, end);
        if (!start.isBefore(end) && current.isAfter(start)) {
            endDateTime = endDateTime.plusDays(1);
        }
        return endDateTime.atZone(ZoneId.systemDefault()).toInstant();
    }

    private boolean isWithinDnd(LocalTime current, LocalTime start, LocalTime end) {
        if (start.equals(end)) {
            return false;
        }
        if (start.isBefore(end)) {
            return !current.isBefore(start) && current.isBefore(end);
        }
        return !current.isBefore(start) || current.isBefore(end);
    }

    private PassengerDtos.ReminderSubscriptionResponse toSubscriptionResponse(ReminderSubscription subscription) {
        return new PassengerDtos.ReminderSubscriptionResponse(
                subscription.getId().toString(),
                subscription.getRouteId().toString(),
                subscription.getStopId().toString(),
                subscription.getReservationName(),
                subscription.getScheduledArrivalAt(),
                subscription.getReminderAt(),
                subscription.getCheckedInAt(),
                subscription.isCanceled(),
                subscription.isReminderSent(),
                subscription.isMissedCheckInSent()
        );
    }

    private String partiallyMask(String content) {
        if (content == null || content.length() <= 8) {
            return "[Internal notice]";
        }
        return content.substring(0, 4) + "..." + content.substring(content.length() - 4);
    }

    private UUID parseUuid(String rawValue, String label) {
        try {
            return UUID.fromString(rawValue);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, label + " identifier is invalid");
        }
    }

    private int scoreRoute(Route route, String query, SearchWeightConfig weights) {
        int score = route.getFrequencyPriority() * weights.getFrequencyWeight();
        if (route.getRouteNumber().equalsIgnoreCase(query)) {
            score += weights.getExactWeight();
        } else if (route.getRouteNumber().toLowerCase(Locale.ROOT).startsWith(query)) {
            score += weights.getPrefixWeight();
        }
        return score;
    }

    private int scoreStop(Stop stop, String query, SearchWeightConfig weights) {
        int score = stop.getPopularity() * weights.getPopularityWeight();
        if (stop.getStopName().equalsIgnoreCase(query)) {
            score += weights.getExactWeight();
        }
        if (stop.getStopName().toLowerCase(Locale.ROOT).startsWith(query)) {
            score += weights.getPrefixWeight();
        }
        if (stop.getStopNamePinyin() != null && stop.getStopNamePinyin().toLowerCase(Locale.ROOT).contains(query)) {
            score += weights.getPinyinWeight();
        }
        if (stop.getStopInitials() != null && stop.getStopInitials().toLowerCase(Locale.ROOT).startsWith(query)) {
            score += weights.getPinyinWeight();
        }
        return score;
    }

    private SearchWeightConfig searchWeights() {
        return searchWeightConfigRepository.findAll().stream().findFirst().orElseGet(() -> {
            SearchWeightConfig fallback = new SearchWeightConfig();
            fallback.setExactWeight(appProperties.search().exactWeight());
            fallback.setPrefixWeight(appProperties.search().prefixWeight());
            fallback.setPinyinWeight(appProperties.search().pinyinWeight());
            fallback.setPopularityWeight(appProperties.search().popularityWeight());
            fallback.setFrequencyWeight(appProperties.search().frequencyWeight());
            return fallback;
        });
    }
}
