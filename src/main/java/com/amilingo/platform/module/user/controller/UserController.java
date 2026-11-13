package com.amilingo.platform.module.user.controller;

import com.amilingo.platform.common.annotation.RateLimit;
import com.amilingo.platform.common.annotation.RequireAuth;
import com.amilingo.platform.common.config.security.SecurityUtil;
import com.amilingo.platform.common.dto.ApiResponse;
import com.amilingo.platform.common.dto.UserDTO;
import com.amilingo.platform.common.dto.request.BindEmailRequest;
import com.amilingo.platform.common.dto.request.BindPhoneRequest;
import com.amilingo.platform.common.dto.request.ChangePasswordRequest;
import com.amilingo.platform.common.dto.request.EmailCodeSendRequest;
import com.amilingo.platform.common.dto.request.EmailValidateRequest;
import com.amilingo.platform.common.dto.request.PhoneValidateRequest;
import com.amilingo.platform.common.dto.request.UpdateProfileRequest;
import com.amilingo.platform.common.exceptions.ApiException;
import com.amilingo.platform.common.exceptions.UserNotFoundException;
import com.amilingo.platform.common.util.StringUtil;
import com.amilingo.platform.component.services.MailService;
import com.amilingo.platform.component.services.SmsService;
import com.amilingo.platform.module.user.entity.user.User;
import com.amilingo.platform.module.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping({"/api/v1/users"})
public class UserController {
    private final UserService userService;
    private final MailService mailService;
    private final SmsService smsService;

    public UserController(UserService userService, MailService mailService, SmsService smsService) {
        this.userService = userService;
        this.mailService = mailService;
        this.smsService = smsService;
    }

    @GetMapping
    public ResponseEntity<?> getUsers(@RequestParam(name = "p") int page, @RequestParam(name = "n") int size){
        SecurityUtil.requireAuthentication();
        if (page < 0 || size <= 0 || size >= 20){
            page = 0;
            size = 10;
        }
        // 他人视角脱敏输出，严禁序列化 User 实体（会泄露 passwordHash）
        List<UserDTO> users = userService.getAllUsers(Pageable.ofSize(size).withPage(page))
                .getContent()
                .stream()
                .map(UserDTO::publicFromUser)
                .toList();
        return ResponseEntity.ok(users);
    }

    @RequireAuth
    @RateLimit(maxRequests = 1)
    @PostMapping("/send")
    public ApiResponse<?> sendEmailValidationCode(@RequestBody EmailCodeSendRequest request){
        mailService.sendEmailBindingCode(request.getEmail());

        return ApiResponse.ok("验证码已发送，请查收邮箱");
    }

    /**
     * 当前登录用户资料（本人视角）。
     */
    @RequireAuth
    @GetMapping("/me")
    public ApiResponse<UserDTO> me() {
        UserDTO user = UserDTO.fromUser(requireCurrentUser());
        return ApiResponse.ok(user);
    }

    /**
     * 修改当前登录用户资料：用户名 / 昵称 / 头像 / 个人介绍。
     * PATCH 语义：仅提交的字段会被修改。
     */
    @RequireAuth
    @RateLimit(maxRequests = 10)
    @PatchMapping("/me")
    public ApiResponse<UserDTO> updateProfile(@RequestBody UpdateProfileRequest request) {
        UserDTO updated = UserDTO.fromUser(
                userService.updateProfile(SecurityUtil.getCurrentUserId(), request));
        return ApiResponse.ok("资料已更新", updated);
    }

    /**
     * 绑定/换绑邮箱 第一步：向待绑定的新邮箱发送验证码。
     */
    @RequireAuth
    @RateLimit(maxRequests = 3)
    @PostMapping("/me/email/bind")
    public ApiResponse<Void> sendBindEmailCode(@RequestBody BindEmailRequest request) {
        mailService.sendEmailBindingCode(request.getEmail());
        return ApiResponse.ok("绑定验证码已发送至新邮箱", null);
    }

    /**
     * 绑定/换绑邮箱 第二步：提交邮箱验证码完成绑定。
     */
    @RequireAuth
    @RateLimit(maxRequests = 10)
    @PostMapping("/me/email/bind/confirm")
    public ApiResponse<UserDTO> confirmBindEmail(@RequestBody EmailValidateRequest request) {
        if (!mailService.validateEmailCode(request.getEmail(), request.getCode())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "验证码错误或已过期");
        }
        UserDTO updated = UserDTO.fromUser(
                userService.bindEmail(SecurityUtil.getCurrentUserId(), request.getEmail()));
        return ApiResponse.ok("邮箱绑定成功", updated);
    }

    /**
     * 绑定/换绑手机号 第一步：向待绑定的新手机号发送短信验证码。
     */
    @RequireAuth
    @RateLimit(maxRequests = 3)
    @PostMapping("/me/phone/bind")
    public ApiResponse<Void> sendBindPhoneCode(@RequestBody BindPhoneRequest request) {
        smsService.sendBindCode(request.getPhone());
        return ApiResponse.ok("绑定验证码已发送", null);
    }

    /**
     * 绑定/换绑手机号 第二步：提交短信验证码完成绑定。
     */
    @RequireAuth
    @RateLimit(maxRequests = 10)
    @PostMapping("/me/phone/bind/confirm")
    public ApiResponse<UserDTO> confirmBindPhone(@RequestBody PhoneValidateRequest request) {
        if (!smsService.verifyBindCode(request.getPhone(), request.getCode())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "验证码错误或已过期");
        }
        UserDTO updated = UserDTO.fromUser(
                userService.bindPhone(SecurityUtil.getCurrentUserId(), request.getPhone()));
        return ApiResponse.ok("手机号绑定成功", updated);
    }

    @RequireAuth
    @PatchMapping("/password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request){
        if (request.password() == null || request.passwordConfirm() == null){
            throw new ApiException(HttpStatus.BAD_REQUEST, "密码不能为空");
        }
        if (!request.password().equals(request.passwordConfirm())){
            throw new ApiException(HttpStatus.BAD_REQUEST, "确认密码不匹配");
        }
        if (!StringUtil.withinLength(request.passwordConfirm(), 8, 32)){
            throw new ApiException(HttpStatus.BAD_REQUEST, "密码长度要求8到32位之间");
        }
        if (!StringUtil.isLegalPassword(request.passwordConfirm())){
            throw new ApiException(HttpStatus.BAD_REQUEST, "密码只能包含大小写字母和数字");
        }
        userService.changePassword(request.passwordConfirm(), SecurityUtil.getCurrentUserId());
        return ApiResponse.of(HttpStatus.NO_CONTENT);
    }

    private User requireCurrentUser() {
        User user = userService.getUserById(SecurityUtil.getCurrentUserId());
        if (user == null) {
            throw new UserNotFoundException("用户不存在");
        }
        return user;
    }
}
