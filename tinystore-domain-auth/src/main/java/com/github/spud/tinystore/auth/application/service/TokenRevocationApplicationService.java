package com.github.spud.tinystore.auth.application.service;

import com.github.spud.tinystore.auth.application.dto.RevokeUserTokensCommand;
import com.github.spud.tinystore.auth.application.port.in.TokenRevocationUseCase;
import com.github.spud.tinystore.auth.application.port.out.AuditLogPort;
import com.github.spud.tinystore.auth.application.port.out.AuthorizationStorePort;
import com.github.spud.tinystore.auth.domain.audit.AuditEvent;
import com.github.spud.tinystore.auth.domain.primitives.UserId;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenRevocationApplicationService implements TokenRevocationUseCase {

  private final AuthorizationStorePort authorizationStorePort;
  private final AuditLogPort auditLogPort;

  public TokenRevocationApplicationService(AuthorizationStorePort authorizationStorePort,
      AuditLogPort auditLogPort) {
    this.authorizationStorePort = authorizationStorePort;
    this.auditLogPort = auditLogPort;
  }

  @Override
  @Transactional
  public void revokeUserTokens(RevokeUserTokensCommand command) {
    var userId = UserId.of(command.userId());
    authorizationStorePort.clearAuthorizationsOf(userId);
    auditLogPort.append(AuditEvent.success(userId.value(), null, null,
        "TOKEN_REVOKE", Set.of(), null, null, command.reason()));
  }
}
