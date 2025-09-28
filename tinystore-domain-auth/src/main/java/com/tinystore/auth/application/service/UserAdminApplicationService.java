package com.tinystore.auth.application.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tinystore.auth.application.dto.UserStatusView;
import com.tinystore.auth.application.port.in.UserAdminUseCase;
import com.tinystore.auth.application.port.out.AuditLogPort;
import com.tinystore.auth.application.port.out.UserRepositoryPort;
import com.tinystore.auth.domain.audit.AuditEvent;
import com.tinystore.auth.domain.model.user.MallUser;
import com.tinystore.auth.domain.primitives.PhoneNumber;
import com.tinystore.auth.domain.primitives.UserId;

import java.util.Set;

@Service
public class UserAdminApplicationService implements UserAdminUseCase {

	private final UserRepositoryPort userRepository;
 private final AuditLogPort auditLogPort;

 public UserAdminApplicationService(UserRepositoryPort userRepository, AuditLogPort auditLogPort) {
		this.userRepository = userRepository;
	this.auditLogPort = auditLogPort;
	}

	@Override
	@Transactional
	public void freezeUser(UUID userId) {
		var user = loadUser(userId);
		user.freeze();
		userRepository.update(user);
	 auditLogPort.append(AuditEvent.success(user.getId().getValue(), user.getPhone().getValue(), null,
	  "USER_FREEZE", Set.of(), null, null, null));
	}

	@Override
	@Transactional
	public void unfreezeUser(UUID userId) {
		var user = loadUser(userId);
		user.unfreeze();
		userRepository.update(user);
	 auditLogPort.append(AuditEvent.success(user.getId().getValue(), user.getPhone().getValue(), null,
	  "USER_UNFREEZE", Set.of(), null, null, null));
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