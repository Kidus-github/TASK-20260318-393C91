package com.citybus.platform.infrastructure.persistence;

import com.citybus.platform.domain.DiagnosticReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DiagnosticReportRepository extends JpaRepository<DiagnosticReport, UUID> {
    List<DiagnosticReport> findTop50ByOrderByGeneratedAtDesc();
}
