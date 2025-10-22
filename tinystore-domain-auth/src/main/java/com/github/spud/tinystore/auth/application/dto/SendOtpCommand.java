package com.github.spud.tinystore.auth.application.dto;

public record SendOtpCommand(String phone,
                             String requestId,
                             String ip,
                             String userAgent) {

}