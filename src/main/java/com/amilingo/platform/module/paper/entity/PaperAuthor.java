package com.amilingo.platform.module.paper.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** 论文作者、署名顺序与贡献信息。 */
@Entity @Table(name="paper_author", uniqueConstraints=@UniqueConstraint(name="uk_paper_author_order", columnNames={"paper_id","author_order"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaperAuthor {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="paper_id", nullable=false) private Long paperId;
    private Long userId;
    @Column(nullable=false) private String name;
    private String institution;
    @Column(name="author_order", nullable=false) private Integer authorOrder;
    @Column(nullable=false) private Boolean coFirstAuthor;
    @Column(nullable=false) private Boolean correspondingAuthor;
    @Column(columnDefinition="TEXT") private String contribution;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist void onCreate(){ if(coFirstAuthor==null)coFirstAuthor=false; if(correspondingAuthor==null)correspondingAuthor=false; createdAt=updatedAt=LocalDateTime.now(); }
    @PreUpdate void onUpdate(){ updatedAt=LocalDateTime.now(); }
}
