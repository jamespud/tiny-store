package com.tinystore.auth.application.dto;

public record SendOtpCommand(String phone,
		String requestId,
		String ip,
		String userAgent) {
}