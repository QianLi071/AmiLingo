package com.amilingo.platform.common.util;

import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 登录安全指纹工具：从请求中提取尽可能多的稳定"标签"（客户端 IP / User-Agent /
 * Accept-Language / Sec-CH-UA / Sec-CH-UA-Platform / 客户端自报告设备ID），
 * 计算 SHA-256 登录指纹，用于"常用位置与设备免二次验证"的判断。
 *
 * <p>安全设计：</p>
 * <ul>
 *   <li>指纹同时包含"位置"（客户端 IP）与"设备"（UA 等设备标签）信息，二者任一变化
 *       都会产生不同指纹，即被视为陌生登录环境，需要邮箱验证码二次验证；</li>
 *   <li>指纹为整体单向哈希，Redis 中不落明文 IP / UA 等个人信息；</li>
 *   <li>标签缺失时以空串参与拼接，保证同一环境下指纹稳定可复现。</li>
 * </ul>
 */
public final class RequestFingerprintUtil {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private RequestFingerprintUtil() {
    }

    /**
     * 解析客户端真实 IP：依次尝试 X-Forwarded-For（取最左侧原始 IP）、X-Real-IP，
     * 最后回退到 remoteAddr。
     */
    public static String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For 可能含多个 IP，取最左侧的原始客户端 IP
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 计算登录指纹（位置 + 设备）。所有可得标签按固定顺序拼接后做 SHA-256。
     *
     * @param request        当前 HTTP 请求
     * @param clientDeviceId 客户端自报告的设备 ID（可空），建议前端持久化于 localStorage
     */
    public static String computeLoginFingerprint(HttpServletRequest request, String clientDeviceId) {
        String tags = String.join("\n",
                safe(resolveClientIp(request)),
                safe(header(request, "User-Agent")),
                safe(header(request, "Accept-Language")),
                safe(header(request, "Sec-CH-UA")),
                safe(header(request, "Sec-CH-UA-Platform")),
                safe(clientDeviceId));
        return sha256Hex(tags);
    }

    private static String header(HttpServletRequest request, String name) {
        return request == null ? null : request.getHeader(name);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // JDK 必带 SHA-256，理论不可达；防御性抛出
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
