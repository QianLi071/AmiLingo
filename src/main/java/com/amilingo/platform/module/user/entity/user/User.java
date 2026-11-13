package com.amilingo.platform.module.user.entity.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "users", indexes = {@Index(name = "idx_user_email", columnList = "email"),
        @Index(name = "idx_user_name", columnList = "username"),
        @Index(name = "idx_user_phone", columnList = "phone")},
        uniqueConstraints = {@UniqueConstraint(name = "uk_user_phone", columnNames = "phone")})
public class User {
    @Id
    @Column(name = "user_id", updatable = false, nullable = false, unique = true)
    private Long id;

    @Column(name = "username", unique = true)
    private String username;

    @Column(name = "password", nullable = false)
    private String passwordHash;

    @Column(name = "email", unique = true)
    private String email;

    /**
     * 手机号（手机号验证码登录 / 绑定手机号后写入）。
     * 可为 null（MySQL 唯一索引允许多个 NULL），唯一性由 uk_user_phone 约束兜底。
     */
    @Column(name = "phone")
    private String phone;

    /** 昵称（个人资料） */
    @Column(name = "nickname", length = 64)
    private String nickname;

    /** 头像地址（http(s) 绝对地址或站内相对路径） */
    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    /** 个人介绍 */
    @Column(name = "bio", length = 500)
    private String bio;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
