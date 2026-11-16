package com.amilingo.platform.module.paper.controller;

import com.amilingo.platform.common.dto.Result;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(basePackageClasses = {PaperController.class, PaperWorkflowController.class, PaperManagementController.class})
public class PaperExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public Result<Void> badRequest(RuntimeException e) { return Result.fail(400, e.getMessage()); }
    @ExceptionHandler(SecurityException.class)
    public Result<Void> forbidden(SecurityException e) { return Result.fail(403, e.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> validation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream().findFirst()
                .map(x -> x.getField() + ": " + x.getDefaultMessage()).orElse("请求参数校验失败");
        return Result.fail(400, message);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> unreadable(HttpMessageNotReadableException e) { return Result.fail(400, "请求体格式错误"); }
}
