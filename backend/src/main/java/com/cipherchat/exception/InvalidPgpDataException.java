package com.cipherchat.exception;

import org.springframework.http.HttpStatus;

public class InvalidPgpDataException extends ApiException {

    public InvalidPgpDataException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
