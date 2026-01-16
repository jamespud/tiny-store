package com.github.spud.tinystore.auth.application.service;

import com.github.spud.tinystore.auth.application.dto.AuthResult;
import com.github.spud.tinystore.auth.application.dto.VerifyPasswordCommand;
import com.github.spud.tinystore.auth.application.port.in.PasswordUserCase;
import com.github.spud.tinystore.auth.application.port.out.AuditLogPort;
import com.github.spud.tinystore.auth.domain.audit.AuditEvent;
import com.github.spud.tinystore.auth.domain.exception.UserFrozenException;
import com.github.spud.tinystore.auth.domain.model.user.MallUser;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import com.github.spud.tinystore.auth.infrastructure.feign.AccountServiceFeignClient;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordApplicationService implements PasswordUserCase {

  private final AccountServiceFeignClient accountServiceFeignClient;
  private final PasswordEncoder passwordEncoder;
  private final AuditLogPort auditLogPort;

  public PasswordApplicationService(AccountServiceFeignClient accountServiceFeignClient,
      PasswordEncoder passwordEncoder,
      AuditLogPort auditLogPort) {
    this.accountServiceFeignClient = accountServiceFeignClient;
    this.passwordEncoder = passwordEncoder;
    this.auditLogPort = auditLogPort;
  }

  @Override
  @Transactional(readOnly = true)
  public AuthResult verifyPassword(VerifyPasswordCommand command) {
    PhoneNumber phone = PhoneNumber.of(command.phone());
    return accountServiceFeignClient.getUserByPhone(phone.value())
        .map(userCoreDto -> {
          try {
            // 验证密码
            if (!passwordEncoder.matches(command.password(), userCoreDto.password())) {
              auditLogPort.append(AuditEvent.failure(null, phone.value(), null,
                  "PASSWORD_LOGIN", Set.of(), null, null, "invalid_credentials"));
              throw new IllegalArgumentException("invalid credentials");
            }

            // 检查用户状态
            if (userCoreDto.accountStatus() != 1) {
              auditLogPort.append(AuditEvent.failure(null, phone.value(), null,
                  "PASSWORD_LOGIN", Set.of(), null, null, "user_frozen"));
              throw new UserFrozenException("user is frozen");
            }

            // 转换为MallUser对象
            MallUser user = MallUser.builder()
                .id(com.github.spud.tinystore.auth.domain.primitives.UserId.of(userCoreDto.userId().toString()))
                .phone(phone)
                .username(userCoreDto.account())
                .nickname(userCoreDto.nickname())
                .avatar(userCoreDto.avatarUrl())
                .enabled(userCoreDto.accountStatus() == 1)
                .build();

            auditLogPort.append(AuditEvent.success(user.getId().value(), user.getPhone().value(), null,
                "PASSWORD_LOGIN", Set.of(), null, null, null));
            return new AuthResult(user);
          } catch (RuntimeException ex) {
            throw ex;
          }
        })
        .orElseThrow(() -> {
          auditLogPort.append(AuditEvent.failure(null, phone.value(), null,
              "PASSWORD_LOGIN", Set.of(), null, null, "user not found"));
          return new IllegalArgumentException("user not found");
        });
  }
}
