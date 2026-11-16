package com.amilingo.platform.component.login;

import com.amilingo.platform.common.exceptions.ApiException;
import com.amilingo.platform.common.exceptions.UserNotFoundException;
import com.amilingo.platform.common.util.RequestFingerprintUtil;
import com.amilingo.platform.common.util.StringUtil;
import com.amilingo.platform.component.redis.RedisService;
import com.amilingo.platform.component.services.MailService;
import com.amilingo.platform.module.user.entity.user.User;
import com.amilingo.platform.module.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 登录安全服务：邮箱密码登录链路的"常用位置与设备"信任判断 + 二次验证挑战。
 *
 * <p>链路（密码校验通过后）：</p>
 * <ol>
 *   <li>计算本次登录指纹（客户端 IP + UA/Accept-Language/Sec-CH-UA/客户端设备ID 的 SHA-256，
 *       见 {@link RequestFingerprintUtil}）；</li>
 *   <li>与该用户近期"受信任指纹"列表比对：匹配（位置与设备都相同）→ 直接登录；</li>
 *   <li>不匹配（新设备/新位置/首次登录）→ 创建一次性 challenge（短 TTL），向账号邮箱发送
 *       验证码；用户提交 challengeId + 验证码校验通过后才由 Controller 签发 JWT Cookie，
 *       并将该指纹加入受信任列表（滚动有效期）。</li>
 * </ol>
 *
 * <p>安全设计（防御性编程，安全优先）：</p>
 * <ul>
 *   <li>challengeId 为 256-bit {@link SecureRandom} 不可预测令牌，一次性、短 TTL；</li>
 *   <li>验证码的消费/防爆破由统一验证码模块负责；单个挑战验证码错误达上限即作废整个挑战，
 *       必须重新走密码登录；</li>
 *   <li>challenge 载荷（userId|fingerprint）解析全程防御式校验，损坏数据按过期处理并清理；</li>
 *   <li>受信任指纹列表有界（默认最多 5 个）且滚动过期（默认 30 天），防止无限膨胀；</li>
 *   <li>邮件投递失败时登录失败（fail-closed），绝不跳过二次验证直接放行；</li>
 *   <li>Redis 异常直接向上抛出（fail-closed），不做"当作受信任设备"的降级。</li>
 * </ul>
 */
@Slf4j
@Service
public class LoginSecurityService {

    private static final String PENDING_KEY_PREFIX = "login:pending:";
    private static final String PENDING_FAIL_KEY_PREFIX = "login:pending:fail:";
    private static final String TRUST_KEY_PREFIX = "login:trust:";
    private static final Pattern FINGERPRINT_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private final RedisService redisService;
    private final MailService mailService;
    private final UserService userService;
    private final SecureRandom secureRandom = new SecureRandom();

    private final long challengeTtlSeconds;
    private final long maxVerifyAttempts;
    private final long trustTtlDays;
    private final int trustMaxDevices;

    public LoginSecurityService(
            RedisService redisService,
            MailService mailService,
            UserService userService,
            @Value("${app.security.login.challenge-ttl-seconds:300}") long challengeTtlSeconds,
            @Value("${app.security.login.max-verify-attempts:5}") long maxVerifyAttempts,
            @Value("${app.security.login.trust-ttl-days:30}") long trustTtlDays,
            @Value("${app.security.login.trust-max-devices:5}") int trustMaxDevices) {
        this.redisService = redisService;
        this.mailService = mailService;
        this.userService = userService;
        this.challengeTtlSeconds = challengeTtlSeconds;
        this.maxVerifyAttempts = maxVerifyAttempts;
        this.trustTtlDays = trustTtlDays;
        this.trustMaxDevices = trustMaxDevices;
    }

    /** 一次未完成的登录二次验证挑战 */
    public record LoginChallenge(String challengeId, String maskedEmail) {
    }

    /**
     * 密码校验通过后评估登录环境。
     *
     * @return null 表示受信任环境（位置与设备均与历史一致），可直接签发 Cookie；
     *         否则返回需要邮箱验证码二次验证的挑战（验证码已发送）
     */
    public LoginChallenge evaluate(User user, HttpServletRequest request, String clientDeviceId) {
        Objects.requireNonNull(user, "user must not be null");
        String fingerprint = RequestFingerprintUtil.computeLoginFingerprint(request, clientDeviceId);
        if (isTrustedDevice(user.getId(), fingerprint)) {
            // 受信任设备：滚动续期，维持"常用设备"语义
            redisService.expire(TRUST_KEY_PREFIX + user.getId(), Duration.ofDays(trustTtlDays));
            return null;
        }
        if (!StringUtils.hasText(user.getEmail())) {
            // 邮箱密码登录的账号必有邮箱；防御性兜底，绝不放行
            throw new ApiException(HttpStatus.UNAUTHORIZED, "账号未绑定邮箱，无法完成安全验证");
        }
        String challengeId = newChallengeId();
        String payload = user.getId() + "|" + fingerprint;
        redisService.setValue(PENDING_KEY_PREFIX + challengeId, payload, Duration.ofSeconds(challengeTtlSeconds));
        // 发送验证码；投递失败会抛异常（fail-closed），本次登录终止，挑战随 TTL 自然作废
        mailService.sendLoginStepUpCode(user.getEmail());
        log.info("陌生登录环境，已发起邮箱二次验证: userId={}, challengeId={}", user.getId(), challengeId);
        return new LoginChallenge(challengeId, StringUtil.maskEmail(user.getEmail()));
    }

    /**
     * 校验登录二次验证挑战。成功后：一次性作废挑战 + 将发起登录的指纹记为受信任设备，
     * 返回用户（由调用方签发 Cookie）。
     */
    public User verifyChallenge(String challengeId, String code) {
        PendingChallenge pending = loadPending(challengeId);
        User user = userService.getUserById(pending.userId());
        if (user == null) {
            redisService.deleteValue(PENDING_KEY_PREFIX + challengeId);
            throw new UserNotFoundException("用户不存在");
        }
        if (!StringUtils.hasText(user.getEmail())
                || !mailService.validateLoginStepUpCode(user.getEmail(), code)) {
            onVerifyFailure(challengeId);
            throw new ApiException(HttpStatus.BAD_REQUEST, "验证码错误或已过期");
        }
        // 挑战一次性消费
        redisService.deleteValue(PENDING_KEY_PREFIX + challengeId);
        redisService.deleteValue(PENDING_FAIL_KEY_PREFIX + challengeId);
        // 记发起登录时的指纹（而非本次确认请求的指纹）为受信任设备
        recordTrustedDevice(pending.userId(), pending.fingerprint());
        log.info("登录二次验证通过: userId={}", pending.userId());
        return user;
    }

    /**
     * 对进行中的挑战重发验证码（受统一验证码模块的发送冷却保护）。
     */
    public void resendChallengeCode(String challengeId) {
        PendingChallenge pending = loadPending(challengeId);
        User user = userService.getUserById(pending.userId());
        if (user == null || !StringUtils.hasText(user.getEmail())) {
            redisService.deleteValue(PENDING_KEY_PREFIX + challengeId);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "登录验证会话已失效，请重新登录");
        }
        mailService.sendLoginStepUpCode(user.getEmail());
    }

    private void onVerifyFailure(String challengeId) {
        long fails = redisService.incrementAndExpire(PENDING_FAIL_KEY_PREFIX + challengeId, challengeTtlSeconds);
        if (fails >= maxVerifyAttempts) {
            redisService.deleteValue(PENDING_KEY_PREFIX + challengeId);
            redisService.deleteValue(PENDING_FAIL_KEY_PREFIX + challengeId);
            log.warn("登录二次验证失败次数达上限，挑战已作废: challengeId={}", challengeId);
        }
    }

    private PendingChallenge loadPending(String challengeId) {
        if (!StringUtils.hasText(challengeId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "缺少登录验证会话标识");
        }
        Object raw = redisService.getValue(PENDING_KEY_PREFIX + challengeId);
        PendingChallenge pending = parsePending(raw);
        if (pending == null) {
            // 不存在或载荷损坏：一律按"过期"处理，不区分原因（防探测）；损坏数据顺手清理
            if (raw != null) {
                redisService.deleteValue(PENDING_KEY_PREFIX + challengeId);
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "登录验证会话不存在或已过期，请重新登录");
        }
        return pending;
    }

    private PendingChallenge parsePending(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw);
        String[] parts = value.split("\\|");
        if (parts.length != 2 || !FINGERPRINT_PATTERN.matcher(parts[1]).matches()) {
            log.warn("登录挑战载荷格式非法，已丢弃");
            return null;
        }
        try {
            return new PendingChallenge(Long.parseLong(parts[0]), parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isTrustedDevice(Long userId, String fingerprint) {
        List<Object> trusted = redisService.getList(TRUST_KEY_PREFIX + userId);
        return trusted.stream().map(String::valueOf).anyMatch(fingerprint::equals);
    }

    private void recordTrustedDevice(Long userId, String fingerprint) {
        redisService.appendToListTail(
                TRUST_KEY_PREFIX + userId, fingerprint, trustMaxDevices, Duration.ofDays(trustTtlDays));
    }

    private String newChallengeId() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record PendingChallenge(Long userId, String fingerprint) {
    }
}
