package com.amilingo.platform.module.lang.event;

import com.amilingo.platform.component.redis.RedisDistributedLock;
import com.amilingo.platform.module.lang.service.CyclePlannerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;

/**
 * 进度变更事件的异步消费者：防抖 + 互斥 + 独立事务执行自动重排。
 *
 * <ul>
 *   <li>事务 AFTER_COMMIT 才触发：确保异步线程能读到刚提交的进度。</li>
 *   <li>冷却期：同一周期 1 小时只自动重排一次（学生连勾 5 个任务不会连环重排）。
 *       冷却键抢锁后不主动释放，靠 1h TTL 自动过期。</li>
 *   <li>分布式互斥：同一周期同时只允许一个重排在跑（Redis 锁，TTL 2 分钟防宕机死锁）。</li>
 *   <li>失败只记日志：自动重排异常绝不回抛影响进度接口（事件消费侧也无人可抛）。</li>
 * </ul>
 */
@Slf4j
@Component
public class AutoReplanHandler {

    /** 同一周期自动重排冷却期。 */
    public static final Duration COOLDOWN = Duration.ofHours(1);
    /** 重排执行锁 TTL（槽位扫描通常秒级，2 分钟为宕机保护上限）。 */
    public static final Duration LOCK_TTL = Duration.ofMinutes(2);
    /** 抢执行锁的等待时间。 */
    public static final Duration LOCK_WAIT = Duration.ofSeconds(10);

    private final RedisDistributedLock redisLock;
    private final CyclePlannerService cyclePlannerService;

    public AutoReplanHandler(RedisDistributedLock redisLock, CyclePlannerService cyclePlannerService) {
        this.redisLock = redisLock;
        this.cyclePlannerService = cyclePlannerService;
    }

    @Async("autoReplanExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProgressChanged(TaskProgressChangedEvent event) {
        if (event.cycleId() == null) {
            return; // 旧引擎遗留任务不参与自动重排
        }
        // 冷却：键存在即说明 1 小时内已重排过，直接放弃本次事件。
        // Redis 整体不可用时降级为无冷却继续（单机风险可控，且迁移后 overdue 自然消失、PENDING 去重仍在）。
        boolean redisAvailable = true;
        String cooldownKey = "plan:autoreplan:cooldown:" + event.cycleId();
        String cooldownToken;
        try {
            cooldownToken = redisLock.tryLock(cooldownKey, COOLDOWN);
        } catch (RuntimeException e) {
            redisAvailable = false;
            cooldownToken = null;
            log.warn("Redis 不可用，自动重排冷却降级（无锁继续）cycleId={}: {}", event.cycleId(), e.toString());
        }
        if (redisAvailable && cooldownToken == null) {
            log.debug("自动重排冷却中，跳过 cycleId={}", event.cycleId());
            return;
        }

        String lockKey = "plan:autoreplan:lock:" + event.cycleId();
        String token = null;
        if (redisAvailable) {
            try {
                token = redisLock.lock(lockKey, LOCK_TTL, LOCK_WAIT);
            } catch (RuntimeException e) {
                // 与冷却同源故障：降级无锁执行，不让防抖组件阻断核心能力
                log.warn("Redis 执行锁获取异常，降级无锁继续 cycleId={}: {}", event.cycleId(), e.toString());
            }
            if (token == null && redisAvailable) {
                log.warn("自动重排抢锁超时，跳过 cycleId={}", event.cycleId());
                return;
            }
        }
        try {
            cyclePlannerService.runAutoReplan(event.userId(), event.cycleId());
        } catch (RuntimeException e) {
            // 自动重排是增强能力，失败不能影响主流程；日志保留排障证据
            log.error("自动重排失败 userId={} cycleId={}", event.userId(), event.cycleId(), e);
        } finally {
            if (token != null) {
                try {
                    redisLock.unlock(lockKey, token);
                } catch (RuntimeException ignored) {
                    // 锁释放失败靠 TTL 兜底
                }
            }
        }
    }
}
