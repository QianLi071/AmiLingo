package com.amilingo.platform.module.paper.dto;

import lombok.*;
import java.time.LocalDate;
import java.util.List;

/**
 * 论文台账请求/响应 DTO。
 * 前端用此结构提交新增/编辑，后端也用它返回列表数据。
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaperDTO {

    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 论文标题 */
    private String title;

    /** 作者顺序（第几作者） */
    private Integer authorOrder;

    /** 期刊/会议名称 */
    private String journal;

    /** 级别：SCI Q1 / EI / CCF-A 等 */
    private String level;

    /** 状态：WRITING / SUBMITTED / ACCEPTED / PUBLISHED */
    private String status;

    /** 投稿日期 */
    private LocalDate submissionDate;

    /** 录用日期 */
    private LocalDate acceptanceDate;

    /** 见刊日期 */
    private LocalDate publicationDate;

    /** DOI */
    private String doi;

    /** 附件文件名列表 */
    private List<String> attachments;
}
