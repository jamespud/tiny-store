package com.tinystore.auth.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tinystore.auth.application.dto.RevokeUserTokensCommand;
import com.tinystore.auth.application.port.in.TokenRevocationUseCase;
import com.tinystore.auth.application.port.out.AuthorizationStorePort;
import com.tinystore.auth.application.port.out.UserRepositoryPort;
import com.tinystore.auth.domain.primitives.UserId;
import com.tinystore.auth.domain.service.RefreshTokenVersionService;

@Service
public class TokenRevocationApplicationService implements TokenRevocationUseCase {

	private final UserRepositoryPort userRepository;
	private final AuthorizationStorePort authorizationStorePort;
	private final RefreshTokenVersionService refreshTokenVersionService;

	public TokenRevocationApplicationService(UserRepositoryPort userRepository,
	                                        AuthorizationStorePort authorizationStorePort,
	                                        RefreshTokenVersionService refreshTokenVersionService) {
		this.userRepository = userRepository;
		this.authorizationStorePort = authorizationStorePort;
		this.refreshTokenVersionService = refreshTokenVersionService;
	}

	@Override
	@Transactional
	public void revokeUserTokens(RevokeUserTokensCommand command) {
		var userId = UserId.of(command.userId());
		var user = userRepository.findById(userId)
			.orElseThrow(() -> new IllegalArgumentException("user not found"));
		var event = refreshTokenVersionService.revokeAll(user, command.reason());
		userRepository.update(user);
		authorizationStorePort.clearAuthorizationsOf(event.userId());
	}
}