package com.github.spud.tinystore.auth.application.port.out;

import com.github.spud.tinystore.auth.domain.primitives.ClientId;
import com.github.spud.tinystore.auth.domain.primitives.ScopeName;
import com.github.spud.tinystore.auth.domain.primitives.UserId;

import java.util.Set;

public interface AuthorizationStorePort {

	void clearAuthorizationsOf(UserId userId);

	Set<ScopeName> loadConsent(UserId userId, ClientId clientId);

	void persistConsent(UserId userId, ClientId clientId, Set<ScopeName> scopes);
}