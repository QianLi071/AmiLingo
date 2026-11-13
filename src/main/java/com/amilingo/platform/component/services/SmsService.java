package com.amilingo.platform.component.services;

import com.amilingo.platform.common.exceptions.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 短信验证码服务：基于统一验证码模块 {@link VerificationCodeService}，
 * 仅负责"手机号场景"的投递编排。所有手机号验证码的签发/校验必须走这里。
 */
@Slf4j
@Service
public class SmsService {

    private final VerificationCodeService verificationCodeService;
    private final SmsSender smsSender;

    public SmsService(VerificationCodeService verificationCodeService,
                      ObjectProvider<SmsSender> smsSenderProvider) {
        this.verificationCodeService = verificationCodeService;
        this.smsSender = smsSenderProvider.getIfAvailable();
    }

    /** 发送手机号登录验证码（未登录链路：手机号验证码登录） */
    public void sendLoginCode(String phone) {
        sendCode(VerificationScene.PHONE_LOGIN, phone);
    }

    /** 发送绑定/换绑手机号验证码（已登录链路） */
    public void sendBindCode(String phone) {
        sendCode(VerificationScene.PHONE_BIND, phone);
    }

    /** 校验手机号登录验证码（严格一次性） */
    public boolean verifyLoginCode(String phone, String code) {
        return verificationCodeService.verify(VerificationScene.PHONE_LOGIN, phone, code);
    }

    /** 校验绑定手机号验证码（严格一次性） */
    public boolean verifyBindCode(String phone, String code) {
        return verificationCodeService.verify(VerificationScene.PHONE_BIND, phone, code);
    }

    private void sendCode(VerificationScene scene, String phone) {
        String code = verificationCodeService.issue(scene, phone);
        if (smsSender == null) {
            // 供应商未配置：作废已签发验证码并快速失败，绝不"假装发送成功"
            verificationCodeService.invalidate(scene, phone);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "短信服务未配置，请联系管理员");
        }
        try {
            smsSender.send(phone, code, scene.getDescription());
        } catch (RuntimeException e) {
            // 投递失败：作废验证码，避免用户收到"已发送"却永远校验不过
            verificationCodeService.invalidate(scene, phone);
            throw e;
        }
    }
}
