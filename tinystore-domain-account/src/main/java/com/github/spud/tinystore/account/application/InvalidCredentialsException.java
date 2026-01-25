package com.github.spud.tinystore.account.application;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("invalid_credentials");
    }
}

