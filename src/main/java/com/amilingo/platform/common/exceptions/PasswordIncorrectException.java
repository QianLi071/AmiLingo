package com.amilingo.platform.common.exceptions;

public class PasswordIncorrectException extends RuntimeException{
    public PasswordIncorrectException(String message) {
        super(message);
    }
}
