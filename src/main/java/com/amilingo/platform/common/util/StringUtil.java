package com.amilingo.platform.common.util;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

public abstract class StringUtil extends StringUtils {
    private static final Pattern pattern = Pattern.compile("[A-Za-z0-9]");
    private static final Pattern MULTI_CHAR_PASSWORD_PATTERN = Pattern.compile("[A-Za-z0-9]+");
    /** 中国大陆手机号：1 开头，第二位 3-9，共 11 位数字 */
    private static final Pattern CHINA_MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    public static boolean isLegalPassword(String password) {
        return password != null && MULTI_CHAR_PASSWORD_PATTERN.matcher(password).matches();
    }

    public static boolean withinLength(String s, int min, int max){
        return s != null && s.length() >= min && s.length() <= max;
    }

    /**
     * 校验中国大陆手机号格式（11 位数字）。
     */
    public static boolean isValidChinaMobile(String phone) {
        return phone != null && CHINA_MOBILE_PATTERN.matcher(phone).matches();
    }

    /**
     * 邮箱脱敏：保留首字符与域名，如 a***@gmail.com。空/畸形输入返回 ***，绝不抛异常。
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    /**
     * 手机号脱敏：保留前 3 后 4，如 138****1234。空/畸形输入返回 ***，绝不抛异常。
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
