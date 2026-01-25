package com.github.spud.tinystore.account.interfaces.rest.internal;

import com.github.spud.tinystore.account.application.InvalidCredentialsException;
import com.github.spud.tinystore.account.application.UserDisabledException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.github.spud.tinystore.account.interfaces.rest.internal")
public class InternalExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Void> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @ExceptionHandler(UserDisabledException.class)
    public ResponseEntity<Void> userDisabled() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}

