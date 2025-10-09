package com.github.spud.tinystore.auth.application.dto;

import com.github.spud.tinystore.auth.domain.model.user.MallUserStatus;

import java.time.OffsetDateTime;

public record UserStatusView(
	String userId,
	String phone,
	MallUserStatus status,
	long rtVersion,
	OffsetDateTime updatedAt) {
}