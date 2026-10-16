package com.amilingo.platform.module.lang.controller;

import com.amilingo.platform.common.config.security.SecurityUtil;
import com.amilingo.platform.common.dto.ApiResponse;
import com.amilingo.platform.module.lang.dto.request.ScheduleRequest;
import com.amilingo.platform.module.lang.dto.response.ScheduleView;
import com.amilingo.platform.module.lang.service.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 每周课表接口。 */
@RestController
@RequestMapping("/api/v1/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public ApiResponse<ScheduleView> getSchedule() {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok(scheduleService.getSchedule(userId));
    }

    @PutMapping
    public ApiResponse<ScheduleView> saveSchedule(@Valid @RequestBody ScheduleRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok("课表已保存", scheduleService.saveBlocks(userId, request));
    }
}
