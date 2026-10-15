package com.amilingo.platform.module.lang.dto.request;

/**
 * 任务模块进度回调（接口约定①桩实现）：任务状态变更时推送。
 *
 * @param status        状态：DONE 完成 / PARTIAL 部分完成 / TODO 未完成
 * @param actualMinutes 实际完成工作量（分钟），可空
 */
public record TaskProgressRequest(
        String status,
        Integer actualMinutes
) {
}
