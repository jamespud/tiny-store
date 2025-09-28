package com.tinystore.auth.application.dto;

import com.tinystore.auth.domain.model.user.MallUserStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MallUserView(UUID id,
                           String phone,
                           String nickname,
                           String avatar,
                           MallUserStatus status,
                           long rtVersion,
                           OffsetDateTime createdAt,
                           OffsetDateTime updatedAt) {
}
