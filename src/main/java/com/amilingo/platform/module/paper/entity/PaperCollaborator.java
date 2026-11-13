package com.amilingo.platform.module.paper.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** 论文协作者权限：OWNER / EDITOR / REVIEWER / VIEWER。 */
@Entity @Table(name="paper_collaborator", uniqueConstraints=@UniqueConstraint(name="uk_paper_collaborator", columnNames={"paper_id","user_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaperCollaborator {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="paper_id",nullable=false) private Long paperId;
    @Column(name="user_id",nullable=false) private Long userId;
    @Column(nullable=false) private String role;
    @Column(nullable=false) private String status;
    private Long invitedBy;
    private LocalDateTime createdAt;
    @PrePersist void onCreate(){ if(status==null)status="ACTIVE"; createdAt=LocalDateTime.now(); }
}
