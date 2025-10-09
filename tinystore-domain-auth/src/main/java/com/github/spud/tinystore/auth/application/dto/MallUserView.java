package com.github.spud.tinystore.auth.application.dto;

import com.github.spud.tinystore.auth.domain.model.user.MallUserStatus;

import java.time.OffsetDateTime;

public record MallUserView(
	String id,
	String phone,
	String nickname,
	String avatar,
	MallUserStatus status,
	long rtVersion,
	OffsetDateTime createdAt,
	OffsetDateTime updatedAt) {
}
