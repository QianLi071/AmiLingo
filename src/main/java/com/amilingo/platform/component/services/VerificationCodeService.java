package com.amilingo.platform.component.services;

import com.amilingo.platform.common.exceptions.ApiException;
import com.amilingo.platform.common.util.StringUtil;
import com.amilingo.platform.component.redis.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;

/**
 * 统一验证码模块（全员复用，禁止再各写一套验证码逻辑）。
 *
 * <p>负责验证码的<b>生成、缓存、发送冷却、一次性校验与防爆破</b>；
 * 投递由调用方完成（邮件走 {@link MailService}，短信走 {@link SmsService}）。</p>
 *
 * <p>当前共用链路：</p>
 * <ul>
 *   <li>邮箱密码登录的陌生设备/位置二次验证（{@link VerificationScene#EMAIL_LOGIN_STEP_UP}）；</li>
 *   <li>手机号验证码登录（{@link VerificationScene#PHONE_LOGIN}）；</li>
 *   <li>邮箱/手机号绑定（{@link VerificationScene#EMAIL_BIND} / {@link VerificationScene#PHONE_BIND}）。</li>
 * </ul>
 *
 * <p>安全设计（防御性编程，安全优先）：</p>
 * <ul>
 *   <li>验证码由 {@link SecureRandom} 生成，仅存于 Redis（带 TTL），全链路不落日志、不落库；</li>
 *   <li>同一目标有发送冷却（SETNX 原子实现），防止验证码轰炸；</li>
 *   <li>校验使用常量时间比较（{@link MessageDigest#isEqual}），防时序侧信道；</li>
 *   <li>校验失败按"场景+目标"计数，达到上限即作废验证码，防爆破；</li>
 *   <li>校验成功用 Lua 原子"值匹配才删除"消费验证码，保证严格一次性，防并发重放；</li>
 *   <li>目标（邮箱/手机号）签发与校验两侧统一规范化，杜绝大小写/空白导致的校验绕过。</li>
 * </ul>
 */
@Slf4j
@Service
public class VerificationCodeService {

    private static final String CODE_KEY_PREFIX = "verify:code:";
    private static final String FAIL_KEY_PREFIX = "verify:fail:";
    private static final String COOLDOWN_KEY_PREFIX = "verify:cooldown:";

    private final RedisService redisService;
    private final EmailValidationService emailValidationService;
    private final SecureRandom secureRandom = new SecureRandom();

    private final long codeTtlSeconds;
    private final int codeLength;
    private final long resendCooldownSeconds;
    private final long maxVerifyAttempts;

    public VerificationCodeService(
            RedisService redisService,
            EmailValidationService emailValidationService,
            @Value("${app.security.verify-code.ttl-seconds:300}") long codeTtlSeconds,
            @Value("${app.security.verify-code.length:6}") int codeLength,
            @Value("${app.security.verify-code.resend-cooldown-seconds:60}") long resendCooldownSeconds,
            @Value("${app.security.verify-code.max-verify-attempts:5}") long maxVerifyAttempts) {
        this.redisService = redisService;
        this.emailValidationService = emailValidationService;
        this.codeTtlSeconds = codeTtlSeconds;
        this.codeLength = codeLength;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.maxVerifyAttempts = maxVerifyAttempts;
        if (codeLength < 4 || codeLength > 8) {
            // 防御：过短易被爆破，过长超出常见输入习惯
            throw new IllegalArgumentException("verify-code.length 必须在 4-8 之间");
        }
    }

    /**
     * 为指定场景签发验证码（生成 + 缓存），返回明文验证码，由调用方投递。
     *
     * @throws ApiException 目标格式非法（400）或处于发送冷却期（429）
     */
    public String issue(VerificationScene scene, String rawTarget) {
        String target = normalizeTarget(scene, rawTarget);
        String cooldownKey = COOLDOWN_KEY_PREFIX + scene.name() + ":" + target;
        if (!redisService.setIfAbsent(cooldownKey, "1", Duration.ofSeconds(resendCooldownSeconds))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "验证码发送过于频繁，请" + resendCooldownSeconds + "秒后再试");
        }
        String code = numericCode(codeLength);
        String codeKey = buildCodeKey(scene, target);
        redisService.setValue(codeKey, code, Duration.ofSeconds(codeTtlSeconds));
        // 新验证码覆盖旧验证码，同时重置失败计数
        redisService.deleteValue(buildFailKey(scene, target));
        log.info("验证码已签发: scene={}, target={}, ttl={}s", scene, maskTarget(scene, target), codeTtlSeconds);
        return code;
    }

    /**
     * 作废指定场景的验证码（投递失败时回滚，避免"幽灵验证码"）。
     */
    public void invalidate(VerificationScene scene, String rawTarget) {
        try {
            String target = normalizeTarget(scene, rawTarget);
            redisService.deleteValue(buildCodeKey(scene, target));
            log.info("验证码已作废: scene={}, target={}", scene, maskTarget(scene, target));
        } catch (ApiException e) {
            // 目标非法说明从未签发过，无需作废
            log.debug("作废验证码时目标非法，忽略: {}", e.getMessage());
        }
    }

    /**
     * 校验验证码。成功即消费（严格一次性）；失败累计计数，达上限作废验证码。
     * 目标格式非法或验证码不存在一律返回 false，不区分原因（防探测）。
     */
    public boolean verify(VerificationScene scene, String rawTarget, String code) {
        String target = normalizeTarget(scene, rawTarget);
        if (!isWellFormedCode(code)) {
            return false;
        }
        String codeKey = buildCodeKey(scene, target);
        Object cached = redisService.getValue(codeKey);
        if (cached == null) {
            return false;
        }
        String expected = String.valueOf(cached);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                code.getBytes(StandardCharsets.UTF_8))) {
            recordVerifyFailure(scene, target, codeKey);
            return false;
        }
        // 比较通过后用 Lua 原子"值匹配才删除"消费，防止并发请求用同一验证码重放
        boolean consumed = redisService.deleteIfValueMatches(codeKey, expected);
        if (!consumed) {
            log.warn("验证码已被并发消费: scene={}, target={}", scene, maskTarget(scene, target));
            return false;
        }
        redisService.deleteValue(buildFailKey(scene, target));
        return true;
    }

    private void recordVerifyFailure(VerificationScene scene, String target, String codeKey) {
        long fails = redisService.incrementAndExpire(buildFailKey(scene, target), codeTtlSeconds);
        if (fails >= maxVerifyAttempts) {
            // 失败次数用尽：作废验证码，必须重新发送
            redisService.deleteValue(codeKey);
            redisService.deleteValue(buildFailKey(scene, target));
            log.warn("验证码错误次数达上限已作废: scene={}, target={}", scene, maskTarget(scene, target));
        }
    }

    private boolean isWellFormedCode(String code) {
        if (code == null || code.length() != codeLength) {
            return false;
        }
        for (int i = 0; i < code.length(); i++) {
            if (!Character.isDigit(code.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 统一规范化目标：邮箱走 EmailValidationService（含临时邮箱域名黑名单），
     * 手机号校验中国大陆格式。签发与校验两侧使用同一规则。
     */
    private String normalizeTarget(VerificationScene scene, String rawTarget) {
        if (rawTarget == null || rawTarget.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, channelTargetName(scene) + "不能为空");
        }
        String trimmed = rawTarget.trim();
        if (scene.getChannel() == VerificationChannel.EMAIL) {
            try {
                return emailValidationService.normalize(trimmed);
            } catch (BadRequestException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "邮箱格式不正确");
            }
        }
        if (!StringUtil.isValidChinaMobile(trimmed)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "手机号格式不正确");
        }
        return trimmed;
    }

    private String channelTargetName(VerificationScene scene) {
        return scene.getChannel() == VerificationChannel.EMAIL ? "邮箱" : "手机号";
    }

    private String maskTarget(VerificationScene scene, String target) {
        return scene.getChannel() == VerificationChannel.EMAIL
                ? StringUtil.maskEmail(target)
                : StringUtil.maskPhone(target);
    }

    private String buildCodeKey(VerificationScene scene, String target) {
        return CODE_KEY_PREFIX + scene.name() + ":" + target;
    }

    private String buildFailKey(VerificationScene scene, String target) {
        return FAIL_KEY_PREFIX + scene.name() + ":" + target;
    }

    private String numericCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            code.append(secureRandom.nextInt(10));
        }
        return code.toString();
    }
}
