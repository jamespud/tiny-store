package com.github.spud.tinystore.auth.domain.model.consent;

import com.github.spud.tinystore.auth.domain.event.ConsentChangedEvent;
import com.github.spud.tinystore.auth.domain.primitives.ClientId;
import com.github.spud.tinystore.auth.domain.primitives.ScopeName;
import com.github.spud.tinystore.auth.domain.primitives.UserId;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class UserConsent {

	private final UserId userId;
	private final ClientId clientId;
	private final Set<ScopeName> grantedScopes;
	private OffsetDateTime updatedAt;

	private UserConsent(UserId userId, ClientId clientId, Set<ScopeName> scopes, OffsetDateTime updatedAt) {
		this.userId = Objects.requireNonNull(userId, "userId");
		this.clientId = Objects.requireNonNull(clientId, "clientId");
		this.grantedScopes = new HashSet<>(Objects.requireNonNull(scopes, "scopes"));
		this.updatedAt = Objects.requireNonNullElse(updatedAt, OffsetDateTime.now());
	}

	public static UserConsent create(UserId userId, ClientId clientId, Set<ScopeName> scopes) {
		return new UserConsent(userId, clientId, scopes, OffsetDateTime.now());
	}

	public static UserConsent restore(UserId userId, ClientId clientId, Set<ScopeName> scopes, OffsetDateTime updatedAt) {
		return new UserConsent(userId, clientId, scopes, updatedAt);
	}

	public ConsentChangedEvent grant(Set<ScopeName> scopes) {
		Set<ScopeName> added = new HashSet<>(scopes);
		added.removeAll(grantedScopes);
		if (added.isEmpty()) {
			return null;
		}
		this.grantedScopes.addAll(added);
		this.updatedAt = OffsetDateTime.now();
		return ConsentChangedEvent.granted(userId, clientId, added);
	}

	public ConsentChangedEvent revoke(Set<ScopeName> scopes) {
		Set<ScopeName> removed = new HashSet<>(scopes);
		removed.retainAll(grantedScopes);
		if (removed.isEmpty()) {
			return null;
		}
		this.grantedScopes.removeAll(removed);
		this.updatedAt = OffsetDateTime.now();
		return ConsentChangedEvent.revoked(userId, clientId, removed);
	}

	public UserId getUserId() {
		return userId;
	}

	public ClientId getClientId() {
		return clientId;
	}

	public Set<ScopeName> getGrantedScopes() {
		return Collections.unmodifiableSet(grantedScopes);
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}
}