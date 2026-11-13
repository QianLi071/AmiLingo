package com.amilingo.platform.module.user.controller;

import com.amilingo.platform.common.annotation.RateLimit;
import com.amilingo.platform.common.annotation.RequireAuth;
import com.amilingo.platform.common.dto.ApiResponse;
import com.amilingo.platform.common.dto.UserDTO;
import com.amilingo.platform.common.dto.request.ChallengeResendRequest;
import com.amilingo.platform.common.dto.request.EmailValidateRequest;
import com.amilingo.platform.common.dto.request.LoginRequest;
import com.amilingo.platform.common.dto.request.LoginVerifyRequest;
import com.amilingo.platform.common.dto.request.PhoneCodeSendRequest;
import com.amilingo.platform.common.dto.request.RegisterRequest;
import com.amilingo.platform.common.dto.response.LoginResultDTO;
import com.amilingo.platform.common.exceptions.ApiException;
import com.amilingo.platform.common.util.JwtUtil;
import com.amilingo.platform.component.ILoginStrategy;
import com.amilingo.platform.component.LoginStrategyFactory;
import com.amilingo.platform.component.login.LoginSecurityService;
import com.amilingo.platform.component.services.MailService;
import com.amilingo.platform.component.services.SmsService;
import com.amilingo.platform.module.user.entity.user.User;
import com.amilingo.platform.module.user.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Duration;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("api/v1/auth/portal")
public class AuthController {
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final LoginStrategyFactory loginStrategyFactory;
    private final MailService mailService;
    private final LoginSecurityService loginSecurityService;
    private final SmsService smsService;

    @Autowired
    public AuthController(JwtUtil jwtUtil, UserService userService, LoginStrategyFactory loginStrategyFactory,
                          MailService mailService, LoginSecurityService loginSecurityService, SmsService smsService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.loginStrategyFactory = loginStrategyFactory;
        this.mailService = mailService;
        this.loginSecurityService = loginSecurityService;
        this.smsService = smsService;
    }

    /**
     * 统一登录入口（策略工厂路由）。
     *
     * <p>安全机制：密码类登录（默认 {@code requiresDeviceTrustVerification()=true}）在凭证校验通过后，
     * 还需通过"常用位置与设备"信任检查（IP + UA/Accept-Language/Sec-CH-UA/deviceId 指纹）：</p>
     * <ul>
     *   <li>位置与设备均与历史一致 → 直接签发 HttpOnly Cookie；</li>
     *   <li>否则发起邮箱验证码二次验证（返回 challengeId，<b>不签发 Cookie</b>），
     *       客户端携带 challengeId + 验证码调用 /login/verify 完成登录。</li>
     * </ul>
     * <p>客户端可在 credential 中附带稳定的 {@code deviceId}（建议 localStorage 持久化），
     * 提升指纹稳定性；不传则仅使用服务端可采集的请求标签。</p>
     */
    @CrossOrigin
    @RateLimit
    @PostMapping("/login")
    public ApiResponse<LoginResultDTO> login(@RequestBody LoginRequest formData,
                                             HttpServletRequest request,
                                             HttpServletResponse httpResponse) {
        String loginType = formData.getLoginType();
        Map<String, Object> params = formData.getCredential();
        if (params == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "登录参数缺失");
        }
        ILoginStrategy strategy = loginStrategyFactory.getStrategy(loginType);
        User user = strategy.authenticate(params);
        if (user == null) {
            // 防御：策略实现应通过异常表达失败，不应返回 null
            throw new ApiException(HttpStatus.UNAUTHORIZED, "登录失败");
        }
        if (strategy.requiresDeviceTrustVerification()) {
            LoginSecurityService.LoginChallenge challenge =
                    loginSecurityService.evaluate(user, request, extractDeviceId(params));
            if (challenge != null) {
                log.info("登录触发二次验证: userId={}", user.getId());
                return ApiResponse.ok("检测到新设备或新位置，请使用邮箱验证码完成验证",
                        LoginResultDTO.challenge(challenge.challengeId(), challenge.maskedEmail()));
            }
        }
        issueAuthCookie(user, httpResponse);
        return ApiResponse.ok(LoginResultDTO.direct(user));
    }

    /**
     * 登录二次验证：校验邮箱验证码，通过后才签发登录 Cookie，并将该设备记为受信任。
     */
    @RateLimit(maxRequests = 10)
    @PostMapping("/login/verify")
    public ApiResponse<LoginResultDTO> verifyLogin(@RequestBody LoginVerifyRequest request,
                                                   HttpServletResponse httpResponse) {
        User user = loginSecurityService.verifyChallenge(request.getChallengeId(), request.getCode());
        issueAuthCookie(user, httpResponse);
        return ApiResponse.ok("验证成功，已登录", LoginResultDTO.direct(user));
    }

    /**
     * 对进行中的登录二次验证挑战重发邮箱验证码（受统一冷却限制）。
     */
    @RateLimit(maxRequests = 3)
    @PostMapping("/login/resend")
    public ApiResponse<Void> resendLoginCode(@RequestBody ChallengeResendRequest request) {
        loginSecurityService.resendChallengeCode(request.getChallengeId());
        return ApiResponse.ok("验证码已重新发送", null);
    }

    /**
     * 发送手机号登录验证码（未登录链路；受限流与每手机号冷却保护）。
     */
    @RateLimit(maxRequests = 3)
    @PostMapping("/phone/send")
    public ApiResponse<Void> sendPhoneLoginCode(@RequestBody PhoneCodeSendRequest request) {
        smsService.sendLoginCode(request.getPhone());
        return ApiResponse.ok("验证码已发送", null);
    }

    @RateLimit
    @RequireAuth
    @DeleteMapping("/logout")
    public ResponseEntity<?> logoutUser(HttpServletResponse httpResponse) {
        Cookie userIdCookie = new Cookie("user_id", "");
        Cookie auth = new Cookie("access_token", "");
        userIdCookie.setPath("/");
        userIdCookie.setMaxAge(0);
        auth.setMaxAge(0);
        auth.setPath("/");
        httpResponse.addCookie(userIdCookie);
        httpResponse.addCookie(auth);

        return ApiResponse.of(HttpStatus.NO_CONTENT);
    }

    @RateLimit(maxRequests = 2)
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest payload, HttpServletResponse httpResponse) {
        User registered;
        try {
            registered = userService.register(payload, payload.getPassword());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        if (registered != null) {
            String token = jwtUtil.generateToken(registered);
            ResponseCookie auth = JwtUtil.produceCookie(Duration.ofDays(7), token);
            httpResponse.addHeader(HttpHeaders.SET_COOKIE, auth.toString());
            return ResponseEntity.created(ServletUriComponentsBuilder
                            .fromCurrentRequest()
                            .path("/{id}")
                            .buildAndExpand(registered.getId())
                            .toUri())
                    .body(ApiResponse.ok(UserDTO.fromUser(registered)));
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Information is not completed, cloud not register.");
        }
    }

    @RateLimit(maxRequests = 1)
    @PostMapping("/validate")
    public ApiResponse<?> validateMailCode(@RequestBody EmailValidateRequest request) {
        if (mailService.validateEmailCode(request.getEmail(), request.getCode())) {
            return ApiResponse.ok("验证成功");
        }
        return ApiResponse.error("验证码错误");
    }

    private String extractDeviceId(Map<String, Object> params) {
        Object deviceId = params.get("deviceId");
        return deviceId instanceof String value && !value.isBlank() ? value : null;
    }

    private void issueAuthCookie(User user, HttpServletResponse httpResponse) {
        String token = jwtUtil.generateToken(user);
        ResponseCookie auth = JwtUtil.produceCookie(Duration.ofDays(7), token);
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, auth.toString());
    }
}
