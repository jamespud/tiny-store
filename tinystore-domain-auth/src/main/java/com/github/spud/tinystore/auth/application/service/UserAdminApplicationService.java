package com.github.spud.tinystore.auth.application.service;

import com.github.spud.tinystore.auth.application.dto.UserStatusView;
import com.github.spud.tinystore.auth.application.port.in.UserAdminUseCase;
import com.github.spud.tinystore.auth.application.port.out.AuditLogPort;
import com.github.spud.tinystore.auth.application.port.out.UserRepository;
import com.github.spud.tinystore.auth.domain.audit.AuditEvent;
import com.github.spud.tinystore.auth.domain.model.user.MallUser;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import com.github.spud.tinystore.auth.domain.primitives.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

@Service
public class UserAdminApplicationService implements UserAdminUseCase {

	private final UserRepository userRepository;
	private final AuditLogPort auditLogPort;

	public UserAdminApplicationService(UserRepository userRepository, AuditLogPort auditLogPort) {
		this.userRepository = userRepository;
		this.auditLogPort = auditLogPort;
	}

	@Override
	@Transactional
	public void freezeUser(String userId) {
		var user = loadUser(userId);
		user.freeze();
		userRepository.update(user);
		auditLogPort.append(AuditEvent.success(user.getId().getValue(), user.getPhone().getValue(), null,
			"USER_FREEZE", Set.of(), null, null, null));
	}

	@Override
	@Transactional
	public void unfreezeUser(String userId) {
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
	public Optional<UserStatusView> getStatus(String userId) {
		return userRepository.findById(UserId.of(userId)).map(this::toView);
	}

	private MallUser loadUser(String userId) {
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