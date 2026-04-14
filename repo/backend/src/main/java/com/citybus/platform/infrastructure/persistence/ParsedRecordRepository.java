package com.citybus.platform.infrastructure.persistence;

import com.citybus.platform.domain.ParsedRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ParsedRecordRepository extends JpaRepository<ParsedRecord, UUID> {
    List<ParsedRecord> findByTemplateIdOrderByCreatedAtDesc(UUID templateId);
}
