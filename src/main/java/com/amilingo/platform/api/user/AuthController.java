package com.amilingo.platform.api.user;

import com.amilingo.platform.common.config.security.SecurityUtil;
import com.amilingo.platform.common.dto.UserDTO;
import com.amilingo.platform.common.dto.request.LoginRequest;
import com.amilingo.platform.common.dto.request.RegisterRequest;
import com.amilingo.platform.common.exceptions.ApiException;
import com.amilingo.platform.common.util.JwtUtil;
import com.amilingo.platform.component.ILoginStrategy;
import com.amilingo.platform.component.LoginStrategyFactory;
import com.amilingo.platform.component.services.user.UserService;
import com.amilingo.platform.module.user.entity.user.User;
import jakarta.servlet.http.Cookie;
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
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("api/v1/auth/portal")
public class AuthController {
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private static final String COOKIE_ROOT = "/";
    private final LoginStrategyFactory loginStrategyFactory;

    @Autowired
    public AuthController(JwtUtil jwtUtil, UserService userService, LoginStrategyFactory loginStrategyFactory) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.loginStrategyFactory = loginStrategyFactory;
    }

    @CrossOrigin
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest formData, HttpServletResponse httpResponse) {
        log.info("User response: {}", formData);
        String loginType = formData.getLoginType();
        Map<String, Object> params = formData.getCredential();

        ILoginStrategy strategy = loginStrategyFactory.getStrategy(loginType);
        User user = strategy.authenticate(params);
        if (user != null) {
            String token = jwtUtil.generateToken(user);
            ResponseCookie auth = ResponseCookie.from("access_token", token)
                    .httpOnly(true)
                    .sameSite("Lax")
                    .path(COOKIE_ROOT)
                    .maxAge(Duration.ofDays(7))
                    .build();
            httpResponse.addHeader(HttpHeaders.SET_COOKIE, auth.toString());

            return ResponseEntity.ok(Map.of(
                            "success", true,
                            "access_token", token,
                            "data", UserDTO.fromUser(user)
                    )
            );
        }
        return ResponseEntity.badRequest().body(null);
    }

    @DeleteMapping("/logout")
    public ResponseEntity<?> logoutUser(HttpServletResponse httpResponse) {
        if (!SecurityUtil.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not Logged in.");
        }
        Cookie userIdCookie = new Cookie("user_id", "");
        Cookie auth = new Cookie("access_token", "");
        userIdCookie.setPath("/");
        userIdCookie.setMaxAge(0);
        auth.setMaxAge(0);
        auth.setPath("/");
        httpResponse.addCookie(userIdCookie);
        httpResponse.addCookie(auth);

        return ResponseEntity.ok(Map.of("success", true, "message", "OK"));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest payload) {
        User registered;
        try {
            registered = userService.register(payload, payload.getPassword());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        Map<String, Object> responseBody = new HashMap<>();

        if (registered != null) {
            String token = jwtUtil.generateToken(registered);
            responseBody.put("success", true);
            responseBody.put("data", registered);
            responseBody.put("token", token);

            return ResponseEntity.created(ServletUriComponentsBuilder
                            .fromCurrentRequest()
                            .path("/{id}")
                            .buildAndExpand(registered.getId())
                            .toUri())
                    .body(responseBody);
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Information is not completed, cloud not register.");
        }
    }
}
