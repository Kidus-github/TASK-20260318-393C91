package tests.unit_tests;

import com.citybus.platform.api.dto.PassengerDtos;
import com.citybus.platform.application.ApiException;
import com.citybus.platform.application.AppProperties;
import com.citybus.platform.application.AuditService;
import com.citybus.platform.application.PassengerService;
import com.citybus.platform.domain.ReminderPreference;
import com.citybus.platform.domain.Route;
import com.citybus.platform.domain.Stop;
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
import com.citybus.platform.domain.RoleName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PassengerServiceUnitTest {
    @Mock
    private RouteRepository routeRepository;
    @Mock
    private StopRepository stopRepository;
    @Mock
    private ReminderPreferenceRepository reminderPreferenceRepository;
    @Mock
    private ReminderSubscriptionRepository reminderSubscriptionRepository;
    @Mock
    private RouteStopRepository routeStopRepository;
    @Mock
    private SearchWeightConfigRepository searchWeightConfigRepository;
    @Mock
    private NotificationTemplateRepository notificationTemplateRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private QueuedMessageRepository queuedMessageRepository;
    @Mock
    private AuditService auditService;

    private PassengerService passengerService;
    private AuthenticatedUser passengerUser;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                "test",
                new AppProperties.Security(15, 7, "01234567890123456789012345678901", "01234567890123456789012345678902"),
                new AppProperties.Reminders(10, 15),
                new AppProperties.Queue(3),
                new AppProperties.Search(10, 7, 5, 3, 4),
                new AppProperties.Alerts(25, 500)
        );
        passengerService = new PassengerService(
                routeRepository,
                stopRepository,
                reminderPreferenceRepository,
                reminderSubscriptionRepository,
                routeStopRepository,
                searchWeightConfigRepository,
                notificationTemplateRepository,
                messageRepository,
                queuedMessageRepository,
                auditService,
                properties
        );
        passengerUser = new AuthenticatedUser(UUID.randomUUID(), "passenger", RoleName.PASSENGER, UUID.randomUUID());
    }

    @Test
    void createSubscriptionRejectsInvalidRouteIdentifier() {
        PassengerDtos.ReminderSubscriptionRequest request = new PassengerDtos.ReminderSubscriptionRequest(
                "not-a-uuid",
                UUID.randomUUID().toString(),
                "Route reminder",
                Instant.now().plusSeconds(600).toString()
        );

        ApiException exception = assertThrows(ApiException.class,
                () -> passengerService.createSubscription(passengerUser, request));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatus());
        assertEquals("Route identifier is invalid", exception.getMessage());
    }

    @Test
    void createSubscriptionRejectsWhenRemindersAreDisabled() {
        UUID routeId = UUID.randomUUID();
        UUID stopId = UUID.randomUUID();

        Route route = new Route();
        route.setId(routeId);
        route.setRouteNumber("101");
        route.setRouteName("Riverside");
        route.setFrequencyPriority(3);
        route.setActive(true);

        Stop stop = new Stop();
        stop.setId(stopId);
        stop.setStopName("Central Station");
        stop.setAddress("Central Ave");
        stop.setPopularity(10);
        stop.setActive(true);

        ReminderPreference preference = new ReminderPreference();
        preference.setUserId(passengerUser.userId());
        preference.setEnabled(false);
        preference.setLeadMinutes(10);
        preference.setDndStart("22:00");
        preference.setDndEnd("07:00");
        preference.setUpdatedAt(Instant.now());

        when(routeRepository.findById(routeId)).thenReturn(Optional.of(route));
        when(stopRepository.findById(stopId)).thenReturn(Optional.of(stop));
        when(routeStopRepository.existsLink(routeId, stopId)).thenReturn(true);
        when(reminderPreferenceRepository.findById(passengerUser.userId())).thenReturn(Optional.of(preference));

        PassengerDtos.ReminderSubscriptionRequest request = new PassengerDtos.ReminderSubscriptionRequest(
                routeId.toString(),
                stopId.toString(),
                "Disabled reminder",
                Instant.now().plusSeconds(900).toString()
        );

        ApiException exception = assertThrows(ApiException.class,
                () -> passengerService.createSubscription(passengerUser, request));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatus());
        assertEquals("Arrival reminders are disabled for this user", exception.getMessage());
    }

    @Test
    void markReadReturnsNotFoundWhenMessageDoesNotBelongToUser() {
        UUID messageId = UUID.randomUUID();
        when(messageRepository.findByIdAndUserId(messageId, passengerUser.userId())).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class,
                () -> passengerService.markRead(passengerUser, messageId));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("Message not found", exception.getMessage());
    }

    @Test
    void updatePreferencesReturnsPersistedValues() {
        ReminderPreference existing = new ReminderPreference();
        existing.setUserId(passengerUser.userId());
        existing.setEnabled(true);
        existing.setLeadMinutes(10);
        existing.setDndStart("22:00");
        existing.setDndEnd("07:00");
        existing.setUpdatedAt(Instant.now());
        when(reminderPreferenceRepository.findById(passengerUser.userId())).thenReturn(Optional.of(existing));
        when(reminderPreferenceRepository.save(any(ReminderPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PassengerDtos.ReminderPreferenceResponse response = passengerService.updatePreferences(
                passengerUser,
                new PassengerDtos.ReminderPreferenceRequest(true, 20, "21:00", "06:30")
        );

        assertEquals(true, response.enabled());
        assertEquals(20, response.leadMinutes());
        assertEquals("21:00", response.dndStart());
        assertEquals("06:30", response.dndEnd());
    }
}

