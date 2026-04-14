package com.citybus.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "parsed_records")
@Getter
@Setter
public class ParsedRecord {
    @Id
    private UUID id;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParsedRecordStatus status;

    @Column(name = "source_reference", nullable = false)
    private String sourceReference;

    @Column(name = "normalized_payload", nullable = false, columnDefinition = "TEXT")
    private String normalizedPayload;

    @Column(name = "template_semantic_version")
    private String templateSemanticVersion;

    @Column(name = "template_content_hash")
    private String templateContentHash;

    @Column(name = "cleaning_rule_snapshot", columnDefinition = "TEXT")
    private String cleaningRuleSnapshot;

    @Column(name = "source_log", columnDefinition = "TEXT")
    private String sourceLog;

    @Column(name = "warning_message")
    private String warningMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
