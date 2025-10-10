package com.github.spud.tinystore.auth.application.dto;

import com.github.spud.tinystore.auth.domain.model.user.MallUser;

public record AuthResult(MallUser user) {
}