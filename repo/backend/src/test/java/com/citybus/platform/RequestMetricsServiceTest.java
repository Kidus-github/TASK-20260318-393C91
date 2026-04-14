package com.citybus.platform;

import com.citybus.platform.application.AlertService;
import com.citybus.platform.application.AppProperties;
import com.citybus.platform.application.DiagnosticReportService;
import com.citybus.platform.application.RequestMetricsService;
import com.citybus.platform.domain.DiagnosticReport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestMetricsServiceTest {
    @Test
    void createsAlertAndReportWhenP95ThresholdExceeded() {
        AlertService alertService = mock(AlertService.class);
        DiagnosticReportService reportService = mock(DiagnosticReportService.class);
        DiagnosticReport report = new DiagnosticReport();
        report.setId(UUID.randomUUID());
        report.setGeneratedAt(Instant.now());
        when(reportService.createReport(anyString(), any(Map.class), anyString())).thenReturn(report);

        RequestMetricsService service = new RequestMetricsService(
                alertService,
                reportService,
                new AppProperties(
                        "test",
                        new AppProperties.Security(15, 7, "a".repeat(32), "b".repeat(32)),
                        new AppProperties.Reminders(10, 15),
                        new AppProperties.Queue(3),
                        new AppProperties.Search(10, 7, 5, 3, 4),
                        new AppProperties.Alerts(25, 100)
                )
        );

        for (int i = 0; i < 30; i++) {
            service.record("/api/passenger/search/results", 200, "trace-1");
        }
        verify(reportService, times(30)).createReport(anyString(), any(Map.class), anyString());
        verify(alertService, times(30)).createAlert(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void createsErrorRateReportOnSpike() {
        AlertService alertService = mock(AlertService.class);
        DiagnosticReportService reportService = mock(DiagnosticReportService.class);
        DiagnosticReport report = new DiagnosticReport();
        report.setId(UUID.randomUUID());
        when(reportService.createReport(anyString(), any(Map.class), anyString())).thenReturn(report);
        RequestMetricsService service = new RequestMetricsService(
                alertService,
                reportService,
                new AppProperties(
                        "test",
                        new AppProperties.Security(15, 7, "a".repeat(32), "b".repeat(32)),
                        new AppProperties.Reminders(10, 15),
                        new AppProperties.Queue(3),
                        new AppProperties.Search(10, 7, 5, 3, 4),
                        new AppProperties.Alerts(25, 500)
                )
        );

        for (int i = 0; i < 30; i++) {
            service.recordResponse("/api/passenger/search/results", 500, "trace-2");
        }
        verify(reportService, times(11)).createReport(anyString(), any(Map.class), anyString());
        verify(alertService, times(11)).createAlert(anyString(), anyString(), anyString(), anyString(), anyString());
    }
}
