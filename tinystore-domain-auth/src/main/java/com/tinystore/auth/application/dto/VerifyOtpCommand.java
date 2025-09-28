package com.tinystore.auth.application.dto;

public record VerifyOtpCommand(String phone, String code) {
}