package com.amilingo.platform.common.dto;

import com.amilingo.platform.common.util.StringUtil;
import com.amilingo.platform.module.user.entity.user.User;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long id;
    private String email;
    private String name;
    private String department;
    /** 已脱敏手机号，如 138****1234 */
    private String phone;
    private String nickname;
    private String avatarUrl;
    private String bio;
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 本人视角（含完整邮箱）：登录、注册、/me、资料修改等返回。
     */
    public static UserDTO fromUser(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getUsername())
                .phone(StringUtil.maskPhone(user.getPhone()))
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /**
     * 他人视角（邮箱与手机号均脱敏，不含敏感字段）：用户列表等场景。
     */
    public static UserDTO publicFromUser(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .email(StringUtil.maskEmail(user.getEmail()))
                .name(user.getUsername())
                .phone(StringUtil.maskPhone(user.getPhone()))
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public User toUser() {
        User user = new User();
        user.setId(this.id);
        user.setEmail(this.email);
        user.setUsername(this.name);
        return user;
    }
}
