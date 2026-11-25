package com.amilingo.platform.component.services;

import com.amilingo.platform.common.exceptions.ApiException;
import com.amilingo.platform.common.exceptions.EmailBindingDeliveryException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * 邮件服务：所有 SMTP 发送统一走这里（禁止在别处直接注入 JavaMailSender）。
 *
 * <p>验证码的生成/缓存/校验统一走 {@link VerificationCodeService}（统一验证码模块），
 * 本类只负责邮件投递与场景编排。</p>
 */
@Service
public class MailService {
    private static final String SUBJECT_TEMPLATE = "【Amilingo】%s验证码";
    private static final String BODY_TEMPLATE = """
            [Amilingo] 您正在进行「%s」操作。
            本次验证码为：%s
            验证码%s分钟内有效，请勿将验证码告知他人。
            如非本人操作，请立即修改密码并忽略本邮件 - Amilingo安全中心
            """;

    private final JavaMailSender mailSender;
    private final String host;
    private final String username;
    private final String password;
    private final String from;
    private final String fromName;
    private final EmailValidationService emailValidationService;
    private final VerificationCodeService verificationCodeService;
    private final long codeTtlSeconds;

    public MailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${app.mail.from:}") String from,
            @Value("${app.mail.from-name:Amilingo}") String fromName,
            EmailValidationService emailValidationService,
            VerificationCodeService verificationCodeService,
            @Value("${app.security.verify-code.ttl-seconds:300}") long codeTtlSeconds) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.host = host;
        this.username = username;
        this.password = password;
        this.from = from;
        this.fromName = fromName;
        this.emailValidationService = emailValidationService;
        this.verificationCodeService = verificationCodeService;
        this.codeTtlSeconds = codeTtlSeconds;
    }

    /**
     * 发送"登录安全验证"验证码（邮箱密码登录时检测到陌生设备/位置，发给账号本人邮箱）。
     */
    public void sendLoginStepUpCode(String email) {
        sendCode(VerificationScene.EMAIL_LOGIN_STEP_UP, email);
    }

    /**
     * 发送"绑定/换绑邮箱"验证码（发给待绑定的新邮箱）。
     */
    public void sendEmailBindingCode(String recipient) {
        sendCode(VerificationScene.EMAIL_BIND, recipient);
    }

    /**
     * 校验"登录安全验证"验证码（严格一次性）。
     */
    public boolean validateLoginStepUpCode(String email, String code) {
        return verificationCodeService.verify(VerificationScene.EMAIL_LOGIN_STEP_UP, email, code);
    }

    /**
     * 校验"绑定/换绑邮箱"验证码（严格一次性）。
     */
    public boolean validateEmailCode(String email, String code) {
        return verificationCodeService.verify(VerificationScene.EMAIL_BIND, email, code);
    }

    private void sendCode(VerificationScene scene, String recipient) {
        // issue 内部完成邮箱格式校验、发送冷却检查与验证码缓存
        String code = verificationCodeService.issue(scene, recipient);
        try {
            deliver(scene, recipient, code);
        } catch (RuntimeException e) {
            // 投递失败：作废验证码，避免"幽灵验证码"（已缓存但用户永远收不到）
            verificationCodeService.invalidate(scene, recipient);
            throw e;
        }
    }

    private void deliver(VerificationScene scene, String recipient, String code) {
        if (mailSender == null
                || !StringUtils.hasText(host)
                || !StringUtils.hasText(username)
                || !StringUtils.hasText(password)
                || !StringUtils.hasText(from)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "邮件服务未配置，无法发送验证码");
        }
        try {
            String target = emailValidationService.normalize(recipient);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    false,
                    StandardCharsets.UTF_8.name()
            );
            helper.setFrom(new InternetAddress(from, fromName, StandardCharsets.UTF_8.name()));
            helper.setTo(target);
            helper.setSubject(SUBJECT_TEMPLATE.formatted(scene.getDescription()));
            helper.setText(BODY_TEMPLATE.formatted(
                    scene.getDescription(), code, Math.max(1, codeTtlSeconds / 60)));
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | MailException exception) {
            throw new EmailBindingDeliveryException(exception.toString());
        } catch (BadRequestException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "邮箱地址不可用");
        }
    }
}
