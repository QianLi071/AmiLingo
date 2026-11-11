package com.amilingo.platform.module.patent.entity;
import jakarta.persistence.*;import lombok.*;import java.time.*;
/** 专利法定/内部期限与提醒状态。 */
@Entity @Table(name="patent_deadline") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PatentDeadline{
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY)private Long id;@Column(nullable=false)private Long patentId;private Long relatedRecordId;
 @Column(nullable=false)private String type;@Column(nullable=false)private String title;@Column(nullable=false)private LocalDate dueDate;
 private Integer remindDaysBefore;@Column(nullable=false)private String priority;@Column(nullable=false)private String status;
 private LocalDateTime completedAt;@Column(columnDefinition="TEXT")private String notes;private LocalDateTime createdAt;private LocalDateTime updatedAt;
 @PrePersist void onCreate(){if(remindDaysBefore==null)remindDaysBefore=7;if(priority==null)priority="MEDIUM";if(status==null)status="PENDING";createdAt=updatedAt=LocalDateTime.now();}@PreUpdate void onUpdate(){updatedAt=LocalDateTime.now();}
}
