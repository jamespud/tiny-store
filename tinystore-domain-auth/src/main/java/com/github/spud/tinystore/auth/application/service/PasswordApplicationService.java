package com.github.spud.tinystore.auth.application.service;

import com.github.spud.tinystore.auth.application.dto.AuthResult;
import com.github.spud.tinystore.auth.application.dto.VerifyPasswordCommand;
import com.github.spud.tinystore.auth.application.port.in.PasswordUserCase;
import com.github.spud.tinystore.auth.application.port.out.AuditLogPort;
import com.github.spud.tinystore.auth.domain.audit.AuditEvent;
import com.github.spud.tinystore.auth.domain.exception.UserFrozenException;
import com.github.spud.tinystore.auth.domain.model.user.MallUser;
import com.github.spud.tinystore.auth.domain.model.user.MallUserStatus;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import com.github.spud.tinystore.auth.domain.primitives.RtVersion;
import com.github.spud.tinystore.auth.domain.primitives.UserId;
import com.github.spud.tinystore.auth.infrastructure.feign.AccountServiceFeignClient;
import feign.FeignException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordApplicationService implements PasswordUserCase {

  private final AccountServiceFeignClient accountServiceFeignClient;
  private final AuditLogPort auditLogPort;
  private final String internalCallToken;

  public PasswordApplicationService(AccountServiceFeignClient accountServiceFeignClient,
      AuditLogPort auditLogPort,
      @Value("${tinystore.internal.call-token:changeit}") String internalCallToken) {
    this.accountServiceFeignClient = accountServiceFeignClient;
    this.auditLogPort = auditLogPort;
    this.internalCallToken = internalCallToken;
  }

  @Override
  @Transactional(readOnly = true)
  public AuthResult verifyPassword(VerifyPasswordCommand command) {
    PhoneNumber phone = PhoneNumber.of(command.phone());
    AccountServiceFeignClient.CredentialVerifyResponse verified;
    try {
      verified = accountServiceFeignClient.verifyCredentials(
          internalCallToken,
          new AccountServiceFeignClient.CredentialVerifyRequest(phone.value(), command.password()));
    } catch (FeignException ex) {
      if (ex.status() == 403) {
        auditLogPort.append(AuditEvent.failure(null, phone.value(), null,
            "PASSWORD_LOGIN", Set.of(), null, null, "user_frozen"));
        throw new UserFrozenException("user is frozen");
      }
      auditLogPort.append(AuditEvent.failure(null, phone.value(), null,
          "PASSWORD_LOGIN", Set.of(), null, null, "invalid_credentials"));
      throw new IllegalArgumentException("invalid credentials");
    }

    if (verified == null) {
      auditLogPort.append(AuditEvent.failure(null, phone.value(), null,
          "PASSWORD_LOGIN", Set.of(), null, null, "invalid_credentials"));
      throw new IllegalArgumentException("invalid credentials");
    }

    long rtVersion = verified.credentialVersion() == null ? 1L : verified.credentialVersion();
    MallUser user = MallUser.restore(
        UserId.of(String.valueOf(verified.userId())),
        phone,
        verified.nickname(),
        verified.avatarUrl(),
        null,
        verified.accountStatus() == 1 ? MallUserStatus.ACTIVE : MallUserStatus.FROZEN,
        RtVersion.of(rtVersion)
    );

    auditLogPort.append(AuditEvent.success(user.getId().value(), user.getPhone().value(), null,
        "PASSWORD_LOGIN", Set.of(), null, null, null));
    return new AuthResult(user);
  }
}
