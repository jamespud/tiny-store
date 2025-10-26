package com.github.spud.tinystore.auth.application.service;

import com.github.spud.tinystore.auth.application.dto.AuthResult;
import com.github.spud.tinystore.auth.application.dto.VerifyPasswordCommand;
import com.github.spud.tinystore.auth.application.port.in.PasswordUserCase;
import com.github.spud.tinystore.auth.application.port.out.AuditLogPort;
import com.github.spud.tinystore.auth.application.port.out.LockAndRateLimitPort;
import com.github.spud.tinystore.auth.application.port.out.SmsSenderPort;
import com.github.spud.tinystore.auth.application.port.out.UserRepository;
import com.github.spud.tinystore.auth.domain.audit.AuditEvent;
import com.github.spud.tinystore.auth.domain.model.user.MallUser;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import jakarta.annotation.Resource;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordApplicationService implements PasswordUserCase {

	private static final String ACTION_PASSWORD_LOGIN = "PASSWORD_LOGIN";
	private static final String ACTION_PASSWORD_LOGIN_FAILED = "PASSWORD_LOGIN_FAILED";

	@Resource
	private UserRepository userRepository;
	
	@Resource
	private AuditLogPort auditLogPort;
	
	@Resource
	private PasswordEncoder passwordEncoder;
	
	@Override
	@Transactional(readOnly = true)
	public AuthResult verifyPassword(VerifyPasswordCommand command) {
		PhoneNumber phone = PhoneNumber.of(command.phone());
		MallUser user = userRepository.findByPhone(phone).orElse(null);
		if (user == null) {
			auditLogPort.append(AuditEvent.failure(null, phone.value(), null,
				ACTION_PASSWORD_LOGIN_FAILED, Set.of(), null, null, "user_not_found"));
			throw new IllegalArgumentException("用户不存在");
		}
		try {
			user.ensureActive();
		} catch (RuntimeException ex) {
			auditLogPort.append(AuditEvent.failure(user.getId().value(), user.getPhone().value(), null,
				ACTION_PASSWORD_LOGIN_FAILED, Set.of(), null, null, ex.getMessage()));
			throw ex;
		}
		String hash = user.getPassword();
		if (hash == null || hash.isEmpty() || !passwordEncoder.matches(command.password(), hash)) {
			auditLogPort.append(AuditEvent.failure(user.getId().value(), user.getPhone().value(), null,
				ACTION_PASSWORD_LOGIN_FAILED, Set.of(), null, null, "bad_credentials"));
			throw new IllegalArgumentException("用户名或密码错误");
		}
		auditLogPort.append(AuditEvent.success(user.getId().value(), user.getPhone().value(), null,
			ACTION_PASSWORD_LOGIN, Set.of(), null, null, null));
		return new AuthResult(user);
	}
}
