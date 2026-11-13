package com.amilingo.platform.common.dto;

import lombok.Getter;

/**
 * 统一 HTTP 响应包装体，所有 Controller 均返回此类型。
 * code=200 表示成功，其余表示业务错误。
 */
@Getter
public class Result<T> {

    private final int code;
    private final String message;
    private final T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功，携带数据 */
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    /** 成功，无数据（增删改场景） */
    public static <T> Result<T> ok() {
        return new Result<>(200, "success", null);
    }

    /** 业务失败 */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /** 404 快捷方法 */
    public static <T> Result<T> notFound(String message) {
        return new Result<>(404, message, null);
    }
}
