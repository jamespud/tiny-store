package com.github.spud.tinystore.auth.domain.event;

import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import com.github.spud.tinystore.auth.domain.primitives.UserId;

import java.time.OffsetDateTime;

public record UserRegisteredEvent(UserId userId, PhoneNumber phone, OffsetDateTime occurredAt) {

	public static UserRegisteredEvent of(UserId userId, PhoneNumber phone) {
		return new UserRegisteredEvent(userId, phone, OffsetDateTime.now());
	}
}