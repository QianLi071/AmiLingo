package com.amilingo.platform.module.research.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** 科研项目成员及其项目内角色。 */
@Entity
@Table(name = "research_project_member",
        uniqueConstraints = @UniqueConstraint(name = "uk_research_member", columnNames = {"project_id", "user_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false)
    private Long projectId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false)
    private String role;
    @Column(nullable = false)
    private String status;
    private LocalDateTime joinedAt;
    private LocalDateTime createdAt;

    @PrePersist void onCreate() {
        if (role == null || role.isBlank()) role = "MEMBER";
        if (status == null || status.isBlank()) status = "ACTIVE";
        createdAt = LocalDateTime.now();
        if (joinedAt == null) joinedAt = createdAt;
    }
}
