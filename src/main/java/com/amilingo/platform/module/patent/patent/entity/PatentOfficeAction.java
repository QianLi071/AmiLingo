package com.amilingo.platform.module.patent.entity;
import jakarta.persistence.*;import lombok.*;import java.time.*;
/** 审查意见通知、补正及答复记录。 */
@Entity @Table(name="patent_office_action") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PatentOfficeAction{
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY)private Long id;@Column(nullable=false)private Long patentId;@Column(nullable=false)private Integer roundNo;
 @Column(nullable=false)private String actionType;@Column(nullable=false)private LocalDate noticeDate;@Column(nullable=false)private LocalDate responseDeadline;
 @Column(nullable=false,columnDefinition="LONGTEXT")private String opinionContent;private Long assigneeUserId;@Column(nullable=false)private String status;
 @Column(columnDefinition="LONGTEXT")private String responseContent;private Long responseClaimVersionId;@Column(columnDefinition="TEXT")private String attachments;
 private LocalDateTime respondedAt;private LocalDateTime createdAt;private LocalDateTime updatedAt;
 @PrePersist void onCreate(){if(status==null)status="PENDING";createdAt=updatedAt=LocalDateTime.now();}@PreUpdate void onUpdate(){updatedAt=LocalDateTime.now();}
}
