package com.github.spud.tinystore.auth.application.service;

import com.github.spud.tinystore.auth.application.dto.RevokeUserTokensCommand;
import com.github.spud.tinystore.auth.application.port.in.TokenRevocationUseCase;
import com.github.spud.tinystore.auth.application.port.out.AuditLogPort;
import com.github.spud.tinystore.auth.application.port.out.AuthorizationStorePort;
import com.github.spud.tinystore.auth.application.port.out.OutboxPort;
import com.github.spud.tinystore.auth.application.port.out.UserRepository;
import com.github.spud.tinystore.auth.domain.audit.AuditEvent;
import com.github.spud.tinystore.auth.domain.primitives.UserId;
import com.github.spud.tinystore.auth.domain.service.RefreshTokenVersionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class TokenRevocationApplicationService implements TokenRevocationUseCase {

	private final UserRepository userRepository;
	private final AuthorizationStorePort authorizationStorePort;
	private final RefreshTokenVersionService refreshTokenVersionService;
	private final OutboxPort outboxPort;
	private final AuditLogPort auditLogPort;

	public TokenRevocationApplicationService(UserRepository userRepository,
	                                         AuthorizationStorePort authorizationStorePort,
	                                         RefreshTokenVersionService refreshTokenVersionService,
	                                         OutboxPort outboxPort,
	                                         AuditLogPort auditLogPort) {
		this.userRepository = userRepository;
		this.authorizationStorePort = authorizationStorePort;
		this.refreshTokenVersionService = refreshTokenVersionService;
		this.outboxPort = outboxPort;
		this.auditLogPort = auditLogPort;
	}

	@Override
	@Transactional
	public void revokeUserTokens(RevokeUserTokensCommand command) {
		var userId = UserId.of(command.userId());
		var user = userRepository.findById(userId)
			.orElseThrow(() -> new IllegalArgumentException("user not found"));
		var event = refreshTokenVersionService.revokeAll(user, command.reason());
		userRepository.update(user);
		outboxPort.save(event);
		authorizationStorePort.clearAuthorizationsOf(event.userId());
		auditLogPort.append(AuditEvent.success(user.getId().value(), user.getPhone().value(), null,
			"TOKEN_REVOKE", Set.of(), null, null, command.reason()));
	}
}