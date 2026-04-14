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
@Table(name = "workflow_tasks")
@Getter
@Setter
public class WorkflowTask {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(name = "task_type", nullable = false)
    private String taskType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkflowState state;

    @Column(name = "owner_role", nullable = false)
    private String ownerRole;

    @Column(name = "approval_mode", nullable = false)
    private String approvalMode;

    @Column(name = "required_approvals", nullable = false)
    private int requiredApprovals;

    @Column(name = "approval_count", nullable = false)
    private int approvalCount;

    @Column(name = "current_approvals", nullable = false)
    private int currentApprovals;

    @Column(name = "progress_step", nullable = false)
    private int progressStep;

    @Column(name = "progress_total", nullable = false)
    private int progressTotal;

    @Column(nullable = false)
    private boolean escalated;

    @Column(name = "resubmission_count", nullable = false)
    private int resubmissionCount;

    @Column(name = "approval_group_id")
    private UUID approvalGroupId;

    @Column(name = "parent_task_id")
    private UUID parentTaskId;

    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    @Column(name = "lease_owner_user_id")
    private UUID leaseOwnerUserId;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(length = 4000)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
