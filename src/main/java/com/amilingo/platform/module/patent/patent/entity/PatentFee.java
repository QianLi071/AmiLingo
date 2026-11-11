package com.amilingo.platform.module.patent.entity;
import jakarta.persistence.*;import lombok.*;import java.math.BigDecimal;import java.time.*;
/** 专利申请、代理、审查、授权和年费记录。 */
@Entity @Table(name="patent_fee") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PatentFee{
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY)private Long id;@Column(nullable=false)private Long patentId;@Column(nullable=false)private String type;
 @Column(nullable=false,precision=14,scale=2)private BigDecimal amount;@Column(nullable=false)private String currency;private BigDecimal reductionAmount;
 private LocalDate dueDate;private LocalDate paidDate;@Column(nullable=false)private String status;private String receiptDocumentKey;
 @Column(columnDefinition="TEXT")private String notes;private LocalDateTime createdAt;private LocalDateTime updatedAt;
 @PrePersist void onCreate(){if(currency==null)currency="CNY";if(reductionAmount==null)reductionAmount=BigDecimal.ZERO;if(status==null)status="UNPAID";createdAt=updatedAt=LocalDateTime.now();}@PreUpdate void onUpdate(){updatedAt=LocalDateTime.now();}
}
