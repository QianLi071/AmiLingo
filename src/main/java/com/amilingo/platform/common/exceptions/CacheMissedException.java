package com.amilingo.platform.common.exceptions;

public class CacheMissedException extends RuntimeException {
    public CacheMissedException(String message) {
        super(message);
    }
}
