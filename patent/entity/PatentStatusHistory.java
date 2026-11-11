package com.amilingo.platform.module.patent.entity;
import jakarta.persistence.*;import lombok.*;import java.time.*;
/** 专利状态变化审计记录。 */
@Entity @Table(name="patent_status_history") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PatentStatusHistory{
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private Long patentId; private String previousStatus;
 @Column(nullable=false) private String currentStatus; @Column(nullable=false) private LocalDate effectiveDate;
 @Column(nullable=false) private Long operatorUserId; @Column(columnDefinition="TEXT") private String remark;
 @Column(columnDefinition="TEXT") private String attachments; private LocalDateTime createdAt;
 @PrePersist void onCreate(){createdAt=LocalDateTime.now();}
}
