package com.github.spud.tinystore.auth.infrastructure.persistence.repository;

import com.github.spud.tinystore.auth.infrastructure.persistence.entity.AuthorizationConsent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthorizationConsentRepository extends
  JpaRepository<AuthorizationConsent, AuthorizationConsent.AuthorizationConsentId> {

  Optional<AuthorizationConsent> findByRegisteredClientIdAndPrincipalName(String registeredClientId,
    String principalName);

  void deleteByRegisteredClientIdAndPrincipalName(String registeredClientId, String principalName);
}