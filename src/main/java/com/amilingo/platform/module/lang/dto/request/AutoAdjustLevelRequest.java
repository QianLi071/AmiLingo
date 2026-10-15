package com.amilingo.platform.module.lang.dto.request;

/**
 * 自动调整级别设置请求。
 *
 * @param cycleId 可选；为空时作用于用户当前最新周期
 * @param level   OFF / SMART / FULL
 */
public record AutoAdjustLevelRequest(Long cycleId, String level) {
}
