package com.amilingo.platform.module.interest.controller;

import com.amilingo.platform.common.config.security.SecurityUtil;
import com.amilingo.platform.common.dto.ApiResponse;
import com.amilingo.platform.module.interest.entity.InterestTag;
import com.amilingo.platform.module.interest.entity.UserInterest;
import com.amilingo.platform.module.interest.service.InterestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 兴趣标签接口。 */
@RestController
@RequestMapping("/api/v1/interest")
public class InterestController {

    private final InterestService interestService;

    public InterestController(InterestService interestService) {
        this.interestService = interestService;
    }

    @GetMapping("/tags")
    public ApiResponse<List<InterestTag>> listTags(@RequestParam(required = false) String category) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok(interestService.listTags(category));
    }

    @PostMapping("/tags")
    public ApiResponse<InterestTag> saveTag(@Valid @RequestBody InterestTag tag) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok("标签已保存", interestService.saveTag(tag));
    }

    @GetMapping("/mine")
    public ApiResponse<List<UserInterest>> myInterests() {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok(interestService.myInterests(userId));
    }

    @PutMapping("/mine")
    public ApiResponse<List<UserInterest>> setInterests(@Valid @RequestBody UpdateInterestsRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok("兴趣已更新", interestService.setInterests(userId, request.interests()));
    }

    @GetMapping("/recommend")
    public ApiResponse<List<InterestTag>> recommend() {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok(interestService.recommend(userId));
    }

    /** 更新兴趣请求体。 */
    public record UpdateInterestsRequest(@NotNull(message = "interests 不能为空")
                                         List<InterestService.InterestInput> interests) {
    }
}
