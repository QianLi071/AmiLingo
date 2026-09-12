package com.amilingo.platform.api.user;

import com.amilingo.platform.common.config.security.SecurityUtil;
import com.amilingo.platform.common.dto.ApiResponse;
import com.amilingo.platform.common.exceptions.EmailNotFoundException;
import com.amilingo.platform.component.services.MailService;
import com.amilingo.platform.module.user.entity.user.User;
import com.amilingo.platform.module.user.service.UserService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

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
        if (page <= 0 || size <= 0 || size >= 20){
            page = 1;
            size = 10;
        }
        return ResponseEntity.ok(userService.getAllUsers(Pageable.ofSize(size).withPage(page)).getContent());
    }

    @PostMapping("/send")
    public ApiResponse<?> sendEmailValidationCode(){
        Long userId = SecurityUtil.getCurrentUserId();
        User user = userService.getUserById(userId);
        String email = Optional.of(user.getEmail()).orElseThrow(() -> new EmailNotFoundException("Email not available"));

        mailService.sendEmailBindingCode(email, String.valueOf(userId));

        return ApiResponse.ok("验证码已发送，请查收邮箱");
    }
}
