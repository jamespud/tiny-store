package com.tinystore.auth.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.tinystore.auth.domain.model.user.MallUserStatus;

public record UserStatusView(UUID userId,
		String phone,
		MallUserStatus status,
		long rtVersion,
		OffsetDateTime updatedAt) {
}