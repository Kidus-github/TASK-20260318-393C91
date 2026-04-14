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
@Table(name = "failed_messages")
@Getter
@Setter
public class FailedMessage {
    @Id
    private UUID id;

    @Column(name = "original_queue_id", nullable = false)
    private UUID originalQueueId;

    @Column(name = "error_summary", nullable = false)
    private String errorSummary;

    @Column(name = "payload_hash", nullable = false)
    private String payloadHash;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
