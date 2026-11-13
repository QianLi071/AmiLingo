package com.amilingo.platform.api.user;

import com.amilingo.platform.common.annotation.RateLimit;
import com.amilingo.platform.common.annotation.RequireAuth;
import com.amilingo.platform.common.config.security.SecurityUtil;
import com.amilingo.platform.common.dto.ApiResponse;
import com.amilingo.platform.common.dto.request.ChangePasswordRequest;
import com.amilingo.platform.common.dto.request.EmailCodeSendRequest;
import com.amilingo.platform.common.exceptions.ApiException;
import com.amilingo.platform.common.util.StringUtil;
import com.amilingo.platform.component.services.MailService;
import com.amilingo.platform.module.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping({"/api/v1/users"})
public class UserController {
    private final UserService userService;
    private final MailService mailService;

    public UserController(UserService userService, MailService mailService) {
        this.userService = userService;
        this.mailService = mailService;
    }

    @GetMapping
    public ResponseEntity<?> getUsers(@RequestParam(name = "p") int page, @RequestParam(name = "n") int size){
        SecurityUtil.requireAuthentication();
        if (page < 0 || size <= 0 || size >= 20){
            page = 0;
            size = 10;
        }
        return ResponseEntity.ok(userService.getAllUsers(Pageable.ofSize(size).withPage(page)).getContent());
    }
    @RateLimit(maxRequests = 1)
    @PostMapping("/send")
    public ApiResponse<?> sendEmailValidationCode(@RequestBody EmailCodeSendRequest request){
        mailService.sendEmailBindingCode(request.getEmail());

        return ApiResponse.ok("验证码已发送，请查收邮箱");
    }

    @RequireAuth
    @PatchMapping("/password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request){
        if (request.password().equals(request.passwordConfirm())){
            throw new ApiException(HttpStatus.BAD_REQUEST, "确认密码不匹配");
        }
        if (StringUtil.withinLength(request.passwordConfirm(), 8,32)){
            throw new ApiException(HttpStatus.BAD_REQUEST, "密码长度要求8到32位之间");
        }
        if (!StringUtil.isLegalPassword(request.passwordConfirm())){
            throw new ApiException(HttpStatus.BAD_REQUEST, "密码只能包含大小写字母和数字");
        }
        userService.changePassword(request.passwordConfirm(), SecurityUtil.getCurrentUserId());
        return ApiResponse.of(HttpStatus.NO_CONTENT);
    }
}
