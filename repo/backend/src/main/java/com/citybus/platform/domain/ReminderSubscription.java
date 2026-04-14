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
@Table(name = "reminder_subscriptions")
@Getter
@Setter
public class ReminderSubscription {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "route_id", nullable = false)
    private UUID routeId;

    @Column(name = "stop_id", nullable = false)
    private UUID stopId;

    @Column(name = "reservation_name", nullable = false)
    private String reservationName;

    @Column(name = "scheduled_arrival_at", nullable = false)
    private Instant scheduledArrivalAt;

    @Column(name = "reminder_at", nullable = false)
    private Instant reminderAt;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(nullable = false)
    private boolean canceled;

    @Column(name = "reminder_sent", nullable = false)
    private boolean reminderSent;

    @Column(name = "missed_check_in_sent", nullable = false)
    private boolean missedCheckInSent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
