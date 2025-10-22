package com.github.spud.tinystore.auth.application.dto;

public record RevokeUserTokensCommand(String userId, String reason) {

}