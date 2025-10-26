package com.github.spud.tinystore.auth.domain.event;

import com.github.spud.tinystore.auth.domain.primitives.ClientId;
import com.github.spud.tinystore.auth.domain.primitives.ScopeName;
import com.github.spud.tinystore.auth.domain.primitives.UserId;
import java.time.OffsetDateTime;
import java.util.Set;

public record ConsentChangedEvent(UserId userId,
																	ClientId clientId,
																	Set<ScopeName> addedScopes,
																	Set<ScopeName> removedScopes,
																	OffsetDateTime occurredAt) {

	public ConsentChangedEvent(String userId, String clientId, Set<String> remainingScopes,
		Set<String> revokedScopes, OffsetDateTime now) {
		this(new UserId(userId), new ClientId(clientId),
			remainingScopes.stream().map(ScopeName::of).collect(java.util.stream.Collectors.toSet()),
			revokedScopes.stream().map(ScopeName::of).collect(java.util.stream.Collectors.toSet()),
			now);
	}

	public static ConsentChangedEvent granted(UserId userId, ClientId clientId,
		Set<ScopeName> scopes) {
		return new ConsentChangedEvent(userId, clientId, Set.copyOf(scopes), Set.of(),
			OffsetDateTime.now());
	}

	public static ConsentChangedEvent revoked(UserId userId, ClientId clientId,
		Set<ScopeName> scopes) {
		return new ConsentChangedEvent(userId, clientId, Set.of(), Set.copyOf(scopes),
			OffsetDateTime.now());
	}
}