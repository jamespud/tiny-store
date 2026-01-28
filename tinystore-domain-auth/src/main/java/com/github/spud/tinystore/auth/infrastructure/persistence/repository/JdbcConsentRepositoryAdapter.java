package com.github.spud.tinystore.auth.infrastructure.persistence.repository;

import com.github.spud.tinystore.auth.application.port.out.AuthorizationStorePort;
import com.github.spud.tinystore.auth.application.port.out.ConsentRepositoryPort;
import com.github.spud.tinystore.auth.domain.model.consent.UserConsent;
import com.github.spud.tinystore.auth.domain.primitives.ClientId;
import com.github.spud.tinystore.auth.domain.primitives.UserId;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

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
    authorizationStorePort.persistConsent(consent.getUserId(), consent.getClientId(),
      consent.getGrantedScopes());
    return consent;
  }
}