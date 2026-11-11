package com.amilingo.platform.module.paper.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** 论文主状态变化审计记录。 */
@Entity @Table(name = "paper_status_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaperStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long paperId;
    private String previousStatus;
    @Column(nullable = false) private String currentStatus;
    @Column(nullable = false) private Long operatorUserId;
    @Column(columnDefinition = "TEXT") private String remark;
    private LocalDateTime createdAt;
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }
}
