package com.tinystore.auth.application.port.out;

import java.util.Set;

import com.tinystore.auth.domain.primitives.ClientId;
import com.tinystore.auth.domain.primitives.ScopeName;
import com.tinystore.auth.domain.primitives.UserId;

public interface AuthorizationStorePort {

	void clearAuthorizationsOf(UserId userId);

	Set<ScopeName> loadConsent(UserId userId, ClientId clientId);

	void persistConsent(UserId userId, ClientId clientId, Set<ScopeName> scopes);
}