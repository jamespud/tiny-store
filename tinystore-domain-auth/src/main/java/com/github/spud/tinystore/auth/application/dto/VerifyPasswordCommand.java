package com.github.spud.tinystore.auth.application.dto;

public record VerifyPasswordCommand(String phone, String password) {

}
