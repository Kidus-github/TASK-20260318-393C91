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
@Table(name = "approval_tasks")
@Getter
@Setter
public class ApprovalTask {
    @Id
    private UUID id;

    @Column(name = "parent_task_id", nullable = false)
    private UUID parentTaskId;

    @Column(name = "approval_group_id", nullable = false)
    private UUID approvalGroupId;

    @Column(name = "approver_role", nullable = false)
    private String approverRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalTaskState state;

    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    @Column(name = "lease_owner_user_id")
    private UUID leaseOwnerUserId;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "decided_by_user_id")
    private UUID decidedByUserId;

    @Column(name = "decision_comment")
    private String decisionComment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
