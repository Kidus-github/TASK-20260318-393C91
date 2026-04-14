package com.citybus.platform.infrastructure.persistence;

import com.citybus.platform.domain.ApprovalTask;
import com.citybus.platform.domain.ApprovalTaskState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalTaskRepository extends JpaRepository<ApprovalTask, UUID> {
    List<ApprovalTask> findByApproverRoleAndStateInOrderByUpdatedAtDesc(String approverRole, List<ApprovalTaskState> states);
    Optional<ApprovalTask> findByIdAndApproverRole(UUID id, String approverRole);
    long countByParentTaskIdAndState(UUID parentTaskId, ApprovalTaskState state);
    List<ApprovalTask> findByParentTaskId(UUID parentTaskId);
    List<ApprovalTask> findByLeaseExpiresAtBeforeAndState(Instant cutoff, ApprovalTaskState state);
    boolean existsByParentTaskIdAndDecidedByUserIdAndState(UUID parentTaskId, UUID decidedByUserId, ApprovalTaskState state);
}
