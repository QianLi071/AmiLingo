package com.amilingo.platform.module.research.dto;

import lombok.*;
import java.time.LocalDate;
import java.util.List;

/**
 * 科研项目台账请求/响应 DTO。
 * 前端用此结构体提交新增/编辑数据，后端也用它返回列表数据。
 * attachments 在传输层为 List<String>，存储层转为逗号分隔字符串。
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ResearchProjectDTO {

    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 项目名称 */
    private String projectName;

    /** 本人角色 */
    private String role;

    /** 指导教师 */
    private String supervisor;

    /** 所属实验室 */
    private String laboratory;

    /** 起始日期，格式 yyyy-MM-dd */
    private LocalDate startDate;

    /** 结束日期，格式 yyyy-MM-dd；进行中时可为 null */
    private LocalDate endDate;

    /** 状态：ONGOING / COMPLETED / SUSPENDED */
    private String status;

    /** 成果描述 */
    private String outcome;

    /** 附件文件名列表 */
    private List<String> attachments;
}
