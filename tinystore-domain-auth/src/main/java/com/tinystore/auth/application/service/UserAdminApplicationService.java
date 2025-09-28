package com.tinystore.auth.application.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tinystore.auth.application.dto.UserStatusView;
import com.tinystore.auth.application.port.in.UserAdminUseCase;
import com.tinystore.auth.application.port.out.UserRepositoryPort;
import com.tinystore.auth.domain.model.user.MallUser;
import com.tinystore.auth.domain.primitives.PhoneNumber;
import com.tinystore.auth.domain.primitives.UserId;

@Service
public class UserAdminApplicationService implements UserAdminUseCase {

	private final UserRepositoryPort userRepository;

	public UserAdminApplicationService(UserRepositoryPort userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	@Transactional
	public void freezeUser(UUID userId) {
		var user = loadUser(userId);
		user.freeze();
		userRepository.update(user);
	}

	@Override
	@Transactional
	public void unfreezeUser(UUID userId) {
		var user = loadUser(userId);
		user.unfreeze();
		userRepository.update(user);
	}

	@Override
	public Optional<UserStatusView> findByPhone(String phone) {
		return userRepository.findByPhone(PhoneNumber.of(phone)).map(this::toView);
	}

	@Override
	public Optional<UserStatusView> getStatus(UUID userId) {
		return userRepository.findById(UserId.of(userId)).map(this::toView);
	}

	private MallUser loadUser(UUID userId) {
		return userRepository.findById(UserId.of(userId))
			.orElseThrow(() -> new IllegalArgumentException("user not found"));
	}

	private UserStatusView toView(MallUser user) {
		return new UserStatusView(
			user.getId().getValue(),
			user.getPhone().getValue(),
			user.getStatus(),
			user.getRtVersion().getValue(),
			user.getUpdatedAt()
		);
	}
}