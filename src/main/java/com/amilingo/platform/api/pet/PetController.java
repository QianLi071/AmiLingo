package com.amilingo.platform.api.pet;

import com.amilingo.platform.common.annotation.RateLimit;
import com.amilingo.platform.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/pets"})
public class PetController {

    /**
     * 示例：同一 IP 在 60 秒内最多访问 5 次，超出返回 429。
     */
    @RateLimit(timeWindow = 60, maxRequests = 5)
    @GetMapping("/info")
    public ApiResponse<String> info() {
        return ApiResponse.ok("宠物信息");
    }
}
