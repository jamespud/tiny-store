package com.tinystore.auth.domain.event;

import java.time.OffsetDateTime;

import com.tinystore.auth.domain.primitives.UserId;

public record UserUnfrozenEvent(UserId userId, OffsetDateTime occurredAt) {

	public static UserUnfrozenEvent of(UserId userId) {
		return new UserUnfrozenEvent(userId, OffsetDateTime.now());
	}
}