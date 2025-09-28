package com.tinystore.auth.domain.event;

import java.time.OffsetDateTime;

import com.tinystore.auth.domain.primitives.RtVersion;
import com.tinystore.auth.domain.primitives.UserId;

public record RefreshTokenRevokedEvent(UserId userId, RtVersion rtVersion, String reason, OffsetDateTime occurredAt) {

	public static RefreshTokenRevokedEvent of(UserId userId, RtVersion rtVersion, String reason) {
		return new RefreshTokenRevokedEvent(userId, rtVersion, reason, OffsetDateTime.now());
	}
}