package com.amilingo.platform.module.career.controller;

import com.amilingo.platform.common.config.security.SecurityUtil;
import com.amilingo.platform.common.dto.ApiResponse;
import com.amilingo.platform.module.career.entity.Application;
import com.amilingo.platform.module.career.entity.School;
import com.amilingo.platform.module.career.service.CareerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 院校与申请接口。 */
@RestController
@RequestMapping("/api/v1/career")
public class CareerController {

    private final CareerService careerService;

    public CareerController(CareerService careerService) {
        this.careerService = careerService;
    }

    @GetMapping("/schools")
    public ApiResponse<List<School>> listSchools() {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok(careerService.listSchools());
    }

    @PostMapping("/schools")
    public ApiResponse<School> saveSchool(@Valid @RequestBody School school) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok("院校已保存", careerService.saveSchool(school));
    }

    @GetMapping("/schools/match")
    public ApiResponse<List<School>> matchSchools(@RequestParam(required = false) Double targetTotal) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok(careerService.matchSchools(targetTotal));
    }

    @GetMapping("/applications")
    public ApiResponse<List<Application>> listApplications() {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok(careerService.listApplications(userId));
    }

    @PostMapping("/applications")
    public ApiResponse<Application> saveApplication(@Valid @RequestBody Application application) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok("申请已保存", careerService.saveApplication(userId, application));
    }
}
