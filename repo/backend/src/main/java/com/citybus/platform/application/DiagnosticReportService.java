package com.citybus.platform.application;

import com.citybus.platform.api.dto.AdminDtos;
import com.citybus.platform.domain.DiagnosticReport;
import com.citybus.platform.infrastructure.persistence.DiagnosticReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DiagnosticReportService {
    private final DiagnosticReportRepository diagnosticReportRepository;
    private final ObjectMapper objectMapper;

    public DiagnosticReportService(DiagnosticReportRepository diagnosticReportRepository, ObjectMapper objectMapper) {
        this.diagnosticReportRepository = diagnosticReportRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DiagnosticReport createReport(String reportType, Map<String, Object> summary, String traceId) {
        DiagnosticReport report = new DiagnosticReport();
        report.setId(UUID.randomUUID());
        report.setReportType(reportType);
        report.setSummaryJson(toJson(summary));
        report.setTraceId(traceId);
        report.setGeneratedAt(Instant.now());
        return diagnosticReportRepository.save(report);
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.DiagnosticReportResponse> latestReports() {
        return diagnosticReportRepository.findTop50ByOrderByGeneratedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminDtos.DiagnosticReportResponse report(UUID id) {
        return diagnosticReportRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Diagnostic report not found"));
    }

    private AdminDtos.DiagnosticReportResponse toResponse(DiagnosticReport report) {
        return new AdminDtos.DiagnosticReportResponse(
                report.getId().toString(),
                report.getReportType(),
                report.getSummaryJson(),
                report.getTraceId(),
                report.getGeneratedAt()
        );
    }

    private String toJson(Map<String, Object> summary) {
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
