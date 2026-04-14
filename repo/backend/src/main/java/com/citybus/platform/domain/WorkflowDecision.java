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
@Table(name = "workflow_decisions")
@Getter
@Setter
public class WorkflowDecision {
    @Id
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "decided_by_user_id", nullable = false)
    private UUID decidedByUserId;

    @Column(nullable = false)
    private String decision;

    @Column(name = "comment_text")
    private String commentText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
