package com.amilingo.platform.module.contest.service;

import com.amilingo.platform.module.contest.entity.ExamEvent;
import com.amilingo.platform.module.contest.entity.SeatSubscription;
import com.amilingo.platform.module.contest.repository.ExamEventRepository;
import com.amilingo.platform.module.contest.repository.SeatSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 考试场次与考位订阅服务。 */
@Service
public class ContestService {

    private final ExamEventRepository examEventRepository;
    private final SeatSubscriptionRepository seatSubscriptionRepository;

    public ContestService(ExamEventRepository examEventRepository,
                          SeatSubscriptionRepository seatSubscriptionRepository) {
        this.examEventRepository = examEventRepository;
        this.seatSubscriptionRepository = seatSubscriptionRepository;
    }

    @Transactional(readOnly = true)
    public List<ExamEvent> listEvents() {
        return examEventRepository.findAllByOrderByTestDateAsc();
    }

    @Transactional(readOnly = true)
    public ExamEvent getEvent(Long id) {
        return examEventRepository.findById(id).orElse(null);
    }

    @Transactional
    public ExamEvent saveEvent(ExamEvent event) {
        return examEventRepository.save(event);
    }

    @Transactional
    public SeatSubscription subscribe(Long userId, Long eventId) {
        if (eventId == null || examEventRepository.findById(eventId).isEmpty()) {
            throw new IllegalArgumentException("考试场次不存在: " + eventId);
        }
        SeatSubscription subscription = seatSubscriptionRepository
                .findByUserIdAndEventId(userId, eventId)
                .orElseGet(() -> SeatSubscription.builder()
                        .userId(userId)
                        .eventId(eventId)
                        .build());
        subscription.setActive(true);
        return seatSubscriptionRepository.save(subscription);
    }

    @Transactional(readOnly = true)
    public List<SeatSubscription> mySubscriptions(Long userId) {
        return seatSubscriptionRepository.findByUserId(userId);
    }

    @Transactional
    public void unsubscribe(Long userId, Long subscriptionId) {
        SeatSubscription subscription = seatSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("订阅不存在: " + subscriptionId));
        if (!userId.equals(subscription.getUserId())) {
            throw new IllegalArgumentException("订阅不属于当前用户");
        }
        subscription.setActive(false);
        seatSubscriptionRepository.save(subscription);
    }

    /**
     * 生成即将放完考位的提醒（只读）：
     * 剩余座位 > 0 且（剩余座位 <= 总座位 10% 或 <= 5）时提醒；同一 eventId 只生成一条。
     */
    @Transactional(readOnly = true)
    public List<String> listDueReminders() {
        List<String> reminders = new ArrayList<>();
        Set<Long> remindedEventIds = new HashSet<>();
        for (SeatSubscription subscription : seatSubscriptionRepository.findByActiveTrue()) {
            Long eventId = subscription.getEventId();
            if (!remindedEventIds.add(eventId)) {
                continue;
            }
            ExamEvent event = examEventRepository.findById(eventId).orElse(null);
            if (event == null || event.getSeatsLeft() == null || event.getSeatsLeft() <= 0) {
                continue;
            }
            int seatsLeft = event.getSeatsLeft();
            boolean lowSeats = seatsLeft <= 5
                    || (event.getSeatsTotal() != null && seatsLeft <= event.getSeatsTotal() * 0.1);
            if (lowSeats) {
                reminders.add("【考位紧张提醒】考试「" + event.getName() + "」（考区："
                        + event.getRegion() + "）当前仅剩 " + seatsLeft + " 个座位，报名截止日期："
                        + event.getRegistrationDeadline() + "，请尽快报名。");
            }
        }
        return reminders;
    }
}
