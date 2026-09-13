package com.amilingo.platform.component.services;

import com.amilingo.platform.common.exceptions.ApiException;
import com.amilingo.platform.common.exceptions.EmailBindingDeliveryException;
import com.amilingo.platform.component.caching.EmailCodeCache;
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
import java.security.SecureRandom;

@Service
public class MailService {
    private static final String SUBJECT = "Amilingo邮箱验证码";

    private static final String BODY = """
                                        [Amilingo] 您正在进行邮箱绑定操作。
                                        本次验证码为：%s
                                        验证码5分钟内有效，请勿将验证码告知他人。
                                        如非本人操作，请忽略本邮件 - TenacityCodeX安全中心
                                        """;

    private final SecureRandom random = new SecureRandom();
    private final JavaMailSender mailSender;
    private final String host;
    private final String username;
    private final String password;
    private final String from;
    private final String fromName;
    private final EmailCodeCache emailCodeCache;
    private final EmailValidationService emailValidationService;

    public MailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${app.mail.from:}") String from,
            @Value("${app.mail.from-name:Amilingo}") String fromName,
            EmailCodeCache emailCodeCache, EmailValidationService emailValidationService) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.host = host;
        this.username = username;
        this.password = password;
        this.from = from;
        this.fromName = fromName;
        this.emailCodeCache = emailCodeCache;
        this.emailValidationService = emailValidationService;
    }

    public void sendEmailBindingCode(String recipient) {
        String code = numericCode(6);
        emailCodeCache.cacheAndExpire(code, recipient);
        if (mailSender == null
                || !StringUtils.hasText(host)
                || !StringUtils.hasText(username)
                || !StringUtils.hasText(password)
                || !StringUtils.hasText(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Email not available");
        }
        try {
            recipient = emailValidationService.normalize(recipient);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    false,
                    StandardCharsets.UTF_8.name()
            );
            helper.setFrom(new InternetAddress(from, fromName, StandardCharsets.UTF_8.name()));
            helper.setTo(recipient);
            helper.setSubject(SUBJECT);

            helper.setText(BODY.formatted(code));
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | MailException exception) {
            throw new EmailBindingDeliveryException(exception.toString());
        } catch (BadRequestException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Email not available");
        }
    }

    public boolean validateEmailCode(String user, String code){
        String cachedCode = emailCodeCache.getAndDeleteCacheById(emailCodeCache.getKeyPrefix()+user);
        return (cachedCode != null && cachedCode.equals(code));
    }

    public String numericCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }
}