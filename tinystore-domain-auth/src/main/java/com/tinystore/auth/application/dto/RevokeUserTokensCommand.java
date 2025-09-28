package com.tinystore.auth.application.dto;

import java.util.UUID;

public record RevokeUserTokensCommand(UUID userId, String reason) {
}