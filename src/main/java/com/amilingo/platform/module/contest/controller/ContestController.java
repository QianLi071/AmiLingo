package com.amilingo.platform.module.contest.controller;

import com.amilingo.platform.common.config.security.SecurityUtil;
import com.amilingo.platform.common.dto.ApiResponse;
import com.amilingo.platform.module.contest.entity.ExamEvent;
import com.amilingo.platform.module.contest.entity.SeatSubscription;
import com.amilingo.platform.module.contest.service.ContestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 考试场次与考位订阅接口。 */
@RestController
@RequestMapping("/api/v1/contest")
public class ContestController {

    private final ContestService contestService;

    public ContestController(ContestService contestService) {
        this.contestService = contestService;
    }

    @GetMapping("/events")
    public ApiResponse<List<ExamEvent>> listEvents() {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok(contestService.listEvents());
    }

    @PostMapping("/events")
    public ApiResponse<ExamEvent> saveEvent(@Valid @RequestBody ExamEvent event) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok("考试场次已保存", contestService.saveEvent(event));
    }

    @GetMapping("/subscriptions")
    public ApiResponse<List<SeatSubscription>> mySubscriptions() {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok(contestService.mySubscriptions(userId));
    }

    @PostMapping("/subscriptions")
    public ApiResponse<SeatSubscription> subscribe(@Valid @RequestBody SubscribeRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok("订阅成功", contestService.subscribe(userId, request.eventId()));
    }

    @PostMapping("/subscriptions/{id}/unsubscribe")
    public ApiResponse<Void> unsubscribe(@PathVariable("id") Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        contestService.unsubscribe(userId, id);
        return ApiResponse.ok("已取消订阅", null);
    }

    /** 订阅请求体。 */
    public record SubscribeRequest(@NotNull(message = "eventId 不能为空") Long eventId) {
    }
}
