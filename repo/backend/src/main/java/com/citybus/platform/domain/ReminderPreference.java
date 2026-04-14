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
@Table(name = "reminder_preferences")
@Getter
@Setter
public class ReminderPreference {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "lead_minutes", nullable = false)
    private int leadMinutes;

    @Column(name = "dnd_start", nullable = false)
    private String dndStart;

    @Column(name = "dnd_end", nullable = false)
    private String dndEnd;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
