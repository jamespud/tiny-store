package com.tinystore.auth.domain.event;

import java.time.OffsetDateTime;

import com.tinystore.auth.domain.primitives.UserId;

public record UserFrozenEvent(UserId userId, OffsetDateTime occurredAt) {

	public static UserFrozenEvent of(UserId userId) {
		return new UserFrozenEvent(userId, OffsetDateTime.now());
	}
}