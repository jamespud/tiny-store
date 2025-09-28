package com.tinystore.auth.infrastructure.persistence.jdbc;

import com.tinystore.auth.application.port.out.AuthorizationStorePort;
import com.tinystore.auth.application.port.out.ConsentRepositoryPort;
import com.tinystore.auth.domain.model.consent.UserConsent;
import com.tinystore.auth.domain.primitives.ClientId;
import com.tinystore.auth.domain.primitives.UserId;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class JdbcConsentRepositoryAdapter implements ConsentRepositoryPort {

	private final AuthorizationStorePort authorizationStorePort;

	public JdbcConsentRepositoryAdapter(AuthorizationStorePort authorizationStorePort) {
		this.authorizationStorePort = authorizationStorePort;
	}

	@Override
	public Optional<UserConsent> find(UserId userId, ClientId clientId) {
		var scopes = authorizationStorePort.loadConsent(userId, clientId);
		if (scopes.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(UserConsent.restore(userId, clientId, scopes, OffsetDateTime.now()));
	}

	@Override
	public UserConsent save(UserConsent consent) {
		authorizationStorePort.persistConsent(consent.getUserId(), consent.getClientId(), consent.getGrantedScopes());
		return consent;
	}
}