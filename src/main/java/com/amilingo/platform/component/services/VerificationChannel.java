package com.amilingo.platform.component.services;

/**
 * 验证码投递渠道。
 */
public enum VerificationChannel {
    /** 邮件（SMTP，走 MailService） */
    EMAIL,
    /** 短信（走 SmsSender） */
    SMS
}
