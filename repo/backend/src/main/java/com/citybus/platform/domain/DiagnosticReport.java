package com.citybus.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "diagnostic_reports")
@Getter
@Setter
public class DiagnosticReport {
    @Id
    private UUID id;

    @Column(name = "report_type", nullable = false)
    private String reportType;

    @Column(name = "summary_json", nullable = false, columnDefinition = "TEXT")
    private String summaryJson;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;
}
