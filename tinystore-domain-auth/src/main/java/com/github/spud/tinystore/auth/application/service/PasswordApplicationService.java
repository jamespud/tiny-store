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
    AccountServiceFeignClient.UserCoreDto userCoreDto = accountServiceFeignClient.getUserByPhone(phone.value());
    
    if (userCoreDto == null) {
      auditLogPort.append(AuditEvent.failure(null, phone.value(), null,
          "PASSWORD_LOGIN", Set.of(), null, null, "user not found"));
      throw new IllegalArgumentException("user not found");
    }
    
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
    MallUser user = MallUser.restore(
        UserId.of(String.valueOf(userCoreDto.userId())),
        phone,
        userCoreDto.nickname(),
        userCoreDto.avatarUrl(),
        userCoreDto.password(),
        userCoreDto.accountStatus() == 1 ? MallUserStatus.ACTIVE : MallUserStatus.FROZEN,
        RtVersion.of(1)
    );

    auditLogPort.append(AuditEvent.success(user.getId().value(), user.getPhone().value(), null,
        "PASSWORD_LOGIN", Set.of(), null, null, null));
    return new AuthResult(user);
  }
}
