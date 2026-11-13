package com.amilingo.platform.module.research.controller;

import com.amilingo.platform.common.dto.Result;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

/** 仅处理科研模块抛出的业务和校验异常。 */
@RestControllerAdvice(basePackageClasses = {ResearchProjectController.class, ResearchWorkflowController.class})
public class ResearchExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public Result<Void> badRequest(RuntimeException ex) { return Result.fail(400, ex.getMessage()); }

    @ExceptionHandler(SecurityException.class)
    public Result<Void> forbidden(SecurityException ex) { return Result.fail(403, ex.getMessage()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream().findFirst()
                .map(e -> e.getField() + ": " + e.getDefaultMessage()).orElse("请求参数校验失败");
        return Result.fail(400, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> unreadable(HttpMessageNotReadableException ex) {
        return Result.fail(400, "请求体格式错误");
    }
}
