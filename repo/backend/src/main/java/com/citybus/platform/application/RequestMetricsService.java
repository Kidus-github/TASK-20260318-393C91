package com.citybus.platform.application;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RequestMetricsService {
    private final Map<String, List<Long>> requestDurations = new ConcurrentHashMap<>();
    private final Map<String, List<Integer>> responseCodes = new ConcurrentHashMap<>();
    private final AlertService alertService;
    private final DiagnosticReportService diagnosticReportService;
    private final AppProperties appProperties;

    public RequestMetricsService(AlertService alertService, DiagnosticReportService diagnosticReportService, AppProperties appProperties) {
        this.alertService = alertService;
        this.diagnosticReportService = diagnosticReportService;
        this.appProperties = appProperties;
    }

    public void record(String path, long durationMs, String traceId) {
        List<Long> durations = requestDurations.computeIfAbsent(path, ignored -> new ArrayList<>());
        synchronized (durations) {
            durations.add(durationMs);
            if (durations.size() > 50) {
                durations.remove(0);
            }
            long p95 = p95(durations);
            if (p95 > appProperties.alerts().apiP95ThresholdMs()) {
                Map<String, Object> summary = new HashMap<>();
                summary.put("path", path);
                summary.put("p95Ms", p95);
                summary.put("thresholdMs", appProperties.alerts().apiP95ThresholdMs());
                summary.put("sampleSize", durations.size());
                var report = diagnosticReportService.createReport("API_P95", summary, traceId);
                alertService.createAlert("API_P95", "WARN", "API P95 threshold exceeded",
                        "Path " + path + " reached P95=" + p95 + "ms (reportId=" + report.getId() + ")", traceId);
            }
        }
    }

    public void recordResponse(String path, int statusCode, String traceId) {
        List<Integer> codes = responseCodes.computeIfAbsent(path, ignored -> new ArrayList<>());
        synchronized (codes) {
            codes.add(statusCode);
            if (codes.size() > 100) {
                codes.remove(0);
            }
            long errorCount = codes.stream().filter(code -> code >= 500).count();
            double errorRate = codes.isEmpty() ? 0.0 : (double) errorCount / codes.size();
            if (codes.size() >= 20 && errorRate >= 0.3) {
                Map<String, Object> summary = new HashMap<>();
                summary.put("path", path);
                summary.put("windowSize", codes.size());
                summary.put("errorCount", errorCount);
                summary.put("errorRate", errorRate);
                var report = diagnosticReportService.createReport("ERROR_RATE", summary, traceId);
                alertService.createAlert("ERROR_RATE", "WARN", "API error rate spike detected",
                        "Path " + path + " has 5xx error rate=" + String.format("%.2f", errorRate) + " (reportId=" + report.getId() + ")", traceId);
            }
        }
    }

    private long p95(List<Long> durations) {
        List<Long> sorted = durations.stream().sorted(Comparator.naturalOrder()).toList();
        int index = Math.max(0, (int) Math.ceil(sorted.size() * 0.95) - 1);
        return sorted.get(index);
    }
}
