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
@Table(name = "workflow_rules")
@Getter
@Setter
public class WorkflowRule {
    @Id
    private UUID id;

    @Column(name = "task_type", nullable = false)
    private String taskType;

    @Column(name = "trigger_field", nullable = false)
    private String triggerField;

    @Column(name = "expected_value", nullable = false)
    private String expectedValue;

    @Column(name = "next_task_types", nullable = false)
    private String nextTaskTypes;

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
