package com.citybus.platform.infrastructure.persistence;

import com.citybus.platform.domain.SearchWeightConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SearchWeightConfigRepository extends JpaRepository<SearchWeightConfig, UUID> {
}
