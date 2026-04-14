package com.citybus.platform.infrastructure.persistence;

import com.citybus.platform.domain.CleaningRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CleaningRuleRepository extends JpaRepository<CleaningRule, UUID> {
    List<CleaningRule> findByEnabledTrueOrderByFieldNameAscRuleKeyAsc();
}
