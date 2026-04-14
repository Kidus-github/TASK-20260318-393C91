package com.citybus.platform.infrastructure.persistence;

import com.citybus.platform.domain.ReminderPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReminderPreferenceRepository extends JpaRepository<ReminderPreference, UUID> {
}
