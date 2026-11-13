package com.amilingo.platform.module.patent.entity;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;
/** Patent 与 Research 项目/成果的跨模块关联记录。 */
@Entity @Table(name="patent_project_link",uniqueConstraints=@UniqueConstraint(name="uk_patent_project_output",columnNames={"patent_id","research_project_id","deliverable_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PatentProjectLink{
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="patent_id",nullable=false) private Long patentId;
 @Column(name="research_project_id",nullable=false) private Long researchProjectId;
 @Column(name="deliverable_id") private Long deliverableId;
 private String deliverableType; @Column(columnDefinition="TEXT") private String description;
 @Column(nullable=false) private Long linkedBy; @Column(nullable=false) private String status; private LocalDateTime createdAt;
 @PrePersist void onCreate(){if(status==null)status="ACTIVE";createdAt=LocalDateTime.now();}
}
