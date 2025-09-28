package com.tinystore.auth.domain.event;

import java.time.OffsetDateTime;

import com.tinystore.auth.domain.primitives.PhoneNumber;
import com.tinystore.auth.domain.primitives.UserId;

public record UserRegisteredEvent(UserId userId, PhoneNumber phone, OffsetDateTime occurredAt) {

	public static UserRegisteredEvent of(UserId userId, PhoneNumber phone) {
		return new UserRegisteredEvent(userId, phone, OffsetDateTime.now());
	}
}