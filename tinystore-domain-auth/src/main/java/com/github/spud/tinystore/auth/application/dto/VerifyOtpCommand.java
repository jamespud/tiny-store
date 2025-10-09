package com.github.spud.tinystore.auth.application.dto;

public record VerifyOtpCommand(String phone, String code) {
}