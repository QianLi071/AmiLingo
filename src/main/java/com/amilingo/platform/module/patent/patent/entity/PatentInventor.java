package com.amilingo.platform.module.patent.entity;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;
/** 专利发明人及署名确认信息。 */
@Entity @Table(name="patent_inventor",uniqueConstraints=@UniqueConstraint(name="uk_patent_inventor_order",columnNames={"patent_id","inventor_order"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PatentInventor{
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="patent_id",nullable=false) private Long patentId; private Long userId;
 @Column(nullable=false) private String name; private String institution;
 @Column(name="inventor_order",nullable=false) private Integer inventorOrder;
 @Column(nullable=false) private Boolean firstInventor; @Column(columnDefinition="TEXT") private String contribution;
 private String contactEmail; private String contactPhone; @Column(nullable=false) private String confirmationStatus;
 private LocalDateTime confirmedAt; private LocalDateTime createdAt; private LocalDateTime updatedAt;
 @PrePersist void onCreate(){if(firstInventor==null)firstInventor=inventorOrder!=null&&inventorOrder==1;if(confirmationStatus==null)confirmationStatus="PENDING";createdAt=updatedAt=LocalDateTime.now();}
 @PreUpdate void onUpdate(){updatedAt=LocalDateTime.now();}
}
