package com.amilingo.platform.module.lang.event;

/**
 * 任务进度变更事件。
 *
 * <p>{@code reportProgress} 事务提交后由 {@code AutoReplanHandler} 异步消费，
 * 避免自动重排（槽位扫描/债务降档）拖慢「勾选完成」接口，也保证重排失败不影响进度落库。
 *
 * @param userId  用户
 * @param cycleId 备考周期（旧引擎遗留任务为 null，消费侧直接忽略）
 * @param taskId  任务
 * @param status  新状态 TODO / PARTIAL / DONE
 */
public record TaskProgressChangedEvent(Long userId, Long cycleId, Long taskId, String status) {
}
