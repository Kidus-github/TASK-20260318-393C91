package com.citybus.platform;

import com.citybus.platform.application.AppProperties;
import com.citybus.platform.application.AuditService;
import com.citybus.platform.application.PassengerService;
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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PassengerServiceTest {
    @Test
    void prioritizesExactRouteMatch() {
        Route exact = new Route();
        exact.setId(UUID.randomUUID());
        exact.setRouteNumber("101");
        exact.setRouteName("Central");
        exact.setFrequencyPriority(5);
        exact.setActive(true);

        Stop stop = new Stop();
        stop.setId(UUID.randomUUID());
        stop.setStopName("Central Station");
        stop.setStopNamePinyin("zhongxin");
        stop.setStopInitials("zx");
        stop.setPopularity(1);
        stop.setActive(true);

        RouteRepository routeRepository = mock(RouteRepository.class);
        StopRepository stopRepository = mock(StopRepository.class);
        RouteStopRepository routeStopRepository = mock(RouteStopRepository.class);
        SearchWeightConfigRepository searchWeightConfigRepository = mock(SearchWeightConfigRepository.class);
        when(routeRepository.search(anyString())).thenReturn(List.of(exact));
        when(stopRepository.search(anyString())).thenReturn(List.of(stop));
        when(routeStopRepository.existsLink(exact.getId(), stop.getId())).thenReturn(true);
        when(searchWeightConfigRepository.findAll()).thenReturn(List.of());

        PassengerService service = new PassengerService(
                routeRepository,
                stopRepository,
                mock(ReminderPreferenceRepository.class),
                mock(ReminderSubscriptionRepository.class),
                routeStopRepository,
                searchWeightConfigRepository,
                mock(NotificationTemplateRepository.class),
                mock(MessageRepository.class),
                mock(QueuedMessageRepository.class),
                mock(AuditService.class),
                new AppProperties("test", new AppProperties.Security(15, 7, "a".repeat(32), "b".repeat(32)), new AppProperties.Reminders(10, 15), new AppProperties.Queue(3), new AppProperties.Search(10, 7, 5, 3, 4), new AppProperties.Alerts(25, 500))
        );

        assertEquals("101", service.search("101").results().get(0).primaryText());
    }
}
