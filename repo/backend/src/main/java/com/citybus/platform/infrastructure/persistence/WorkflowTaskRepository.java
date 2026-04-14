package com.citybus.platform.infrastructure.persistence;

import com.citybus.platform.domain.WorkflowState;
import com.citybus.platform.domain.WorkflowTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowTaskRepository extends JpaRepository<WorkflowTask, UUID> {
    List<WorkflowTask> findByOwnerRoleAndStateInOrderByUpdatedAtDesc(String ownerRole, List<WorkflowState> states);
    Optional<WorkflowTask> findByIdAndOwnerRole(UUID id, String ownerRole);
    List<WorkflowTask> findByLeaseExpiresAtBeforeAndState(Instant cutoff, WorkflowState state);
    List<WorkflowTask> findByCreatedAtBeforeAndState(Instant cutoff, WorkflowState state);
}
