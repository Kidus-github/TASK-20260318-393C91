package com.citybus.platform.infrastructure.persistence;

import com.citybus.platform.domain.WorkflowDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkflowDecisionRepository extends JpaRepository<WorkflowDecision, UUID> {
    List<WorkflowDecision> findByTaskIdOrderByCreatedAtAsc(UUID taskId);
    boolean existsByTaskIdAndDecidedByUserIdAndDecision(UUID taskId, UUID decidedByUserId, String decision);
}
