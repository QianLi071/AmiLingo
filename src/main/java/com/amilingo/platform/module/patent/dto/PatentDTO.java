package com.amilingo.platform.module.patent.dto;

import lombok.*;
import java.time.LocalDate;
import java.util.List;

/**
 * 专利台账请求/响应 DTO。
 * 前端用此结构提交新增/编辑，后端也用它返回列表数据。
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PatentDTO {

    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 专利名称 */
    private String name;

    /** 类型：INVENTION / UTILITY / DESIGN */
    private String type;

    /** 申请号 */
    private String applicationNumber;

    /** 授权号 */
    private String grantNumber;

    /** 发明人顺序（第几发明人） */
    private Integer inventorOrder;

    /** 状态：APPLIED / SUBSTANTIVE_EXAM / GRANTED / REJECTED */
    private String status;

    /** 申请日期 */
    private LocalDate applicationDate;

    /** 授权日期 */
    private LocalDate grantDate;

    /** 附件文件名列表 */
    private List<String> attachments;
}
