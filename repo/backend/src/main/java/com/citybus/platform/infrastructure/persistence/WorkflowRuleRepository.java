package com.citybus.platform.infrastructure.persistence;

import com.citybus.platform.domain.WorkflowRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkflowRuleRepository extends JpaRepository<WorkflowRule, UUID> {
    List<WorkflowRule> findByTaskTypeIgnoreCaseAndEnabledTrueOrderByPriorityAsc(String taskType);
}
