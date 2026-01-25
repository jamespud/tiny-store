package com.github.spud.tinystore.account.application;

public class UserDisabledException extends RuntimeException {

    public UserDisabledException() {
        super("user_disabled");
    }
}

