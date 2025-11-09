package com.github.spud.tinystore.auth.application.service;

import com.github.spud.tinystore.auth.application.dto.AuthResult;
import com.github.spud.tinystore.auth.application.dto.VerifyPasswordCommand;
import com.github.spud.tinystore.auth.application.port.in.PasswordUserCase;
import com.github.spud.tinystore.auth.application.port.out.AuditLogPort;
import com.github.spud.tinystore.auth.application.port.out.UserRepository;
import com.github.spud.tinystore.auth.domain.audit.AuditEvent;
import com.github.spud.tinystore.auth.domain.exception.UserFrozenException;
import com.github.spud.tinystore.auth.domain.model.user.MallUser;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordApplicationService implements PasswordUserCase {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuditLogPort auditLogPort;

  public PasswordApplicationService(UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      AuditLogPort auditLogPort) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.auditLogPort = auditLogPort;
  }

  @Override
  @Transactional(readOnly = true)
  public AuthResult verifyPassword(VerifyPasswordCommand command) {
    PhoneNumber phone = PhoneNumber.of(command.phone());
    Optional<MallUser> userOpt = userRepository.findByPhone(phone);
    MallUser user = userOpt.orElseThrow(() -> new IllegalArgumentException("user not found"));
    try {
      user.ensureActive();
    } catch (UserFrozenException ex) {
      auditLogPort.append(AuditEvent.failure(user.getId().value(), user.getPhone().value(), null,
          "PASSWORD_LOGIN", Set.of(), null, null, ex.getMessage()));
      throw ex;
    }
    String hash = user.getPassword();
    if (hash == null || !passwordEncoder.matches(command.password(), hash)) {
      auditLogPort.append(AuditEvent.failure(user.getId().value(), user.getPhone().value(), null,
          "PASSWORD_LOGIN", Set.of(), null, null, "invalid_credentials"));
      throw new IllegalArgumentException("invalid credentials");
    }
    auditLogPort.append(AuditEvent.success(user.getId().value(), user.getPhone().value(), null,
        "PASSWORD_LOGIN", Set.of(), null, null, null));
    return new AuthResult(user);
  }
}
