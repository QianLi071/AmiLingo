package com.amilingo.platform.common.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class LoginFailedException extends ApiException {

    private final Integer attemptsRemaining;
    private final Boolean locked;
    private final Long retryAfterMinutes;

    public LoginFailedException(String message, int attemptsRemaining) {
        super(HttpStatus.BAD_REQUEST, message);
        this.attemptsRemaining = attemptsRemaining;
        this.locked = false;
        this.retryAfterMinutes = null;
    }

    public LoginFailedException(String message, long retryAfterMinutes, boolean locked) {
        super(HttpStatus.LOCKED, message);
        this.attemptsRemaining = 0;
        this.locked = locked;
        this.retryAfterMinutes = retryAfterMinutes;
    }
}
