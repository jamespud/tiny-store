package com.tinystore.auth.domain.event;

import java.time.OffsetDateTime;
import java.util.Set;

import com.tinystore.auth.domain.primitives.ClientId;
import com.tinystore.auth.domain.primitives.ScopeName;
import com.tinystore.auth.domain.primitives.UserId;

public record ConsentChangedEvent(UserId userId,
		ClientId clientId,
		Set<ScopeName> addedScopes,
		Set<ScopeName> removedScopes,
		OffsetDateTime occurredAt) {

	public static ConsentChangedEvent granted(UserId userId, ClientId clientId, Set<ScopeName> scopes) {
		return new ConsentChangedEvent(userId, clientId, Set.copyOf(scopes), Set.of(), OffsetDateTime.now());
	}

	public static ConsentChangedEvent revoked(UserId userId, ClientId clientId, Set<ScopeName> scopes) {
		return new ConsentChangedEvent(userId, clientId, Set.of(), Set.copyOf(scopes), OffsetDateTime.now());
	}
}