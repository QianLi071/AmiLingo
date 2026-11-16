package com.amilingo.platform.common.dto;

import com.amilingo.platform.common.exceptions.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public record ApiResponse<T>(boolean success, String message, T data) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", data);
    }
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }
    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
    public static ResponseEntity<?> of(String message, HttpStatus status){
        return ResponseEntity.status(status).body(message);
    }
    public static ResponseEntity<?> of(HttpStatus status){
        return ResponseEntity.status(status).build();
    }
}