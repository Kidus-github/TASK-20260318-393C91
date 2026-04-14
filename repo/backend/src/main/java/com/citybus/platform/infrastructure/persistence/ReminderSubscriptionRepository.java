package com.citybus.platform.infrastructure.persistence;

import com.citybus.platform.domain.ReminderSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReminderSubscriptionRepository extends JpaRepository<ReminderSubscription, UUID> {
    List<ReminderSubscription> findByUserIdOrderByScheduledArrivalAtAsc(UUID userId);
    Optional<ReminderSubscription> findByIdAndUserId(UUID id, UUID userId);
    List<ReminderSubscription> findByReminderSentFalseAndCanceledFalseAndReminderAtBefore(Instant cutoff);
    List<ReminderSubscription> findByMissedCheckInSentFalseAndCanceledFalseAndCheckedInAtIsNullAndScheduledArrivalAtBefore(Instant cutoff);
}
