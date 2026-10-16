package com.amilingo.platform.module.contest.repository;

import com.amilingo.platform.module.contest.entity.SeatSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeatSubscriptionRepository extends JpaRepository<SeatSubscription, Long> {

    List<SeatSubscription> findByUserId(Long userId);

    List<SeatSubscription> findByActiveTrue();

    Optional<SeatSubscription> findByUserIdAndEventId(Long userId, Long eventId);
}
