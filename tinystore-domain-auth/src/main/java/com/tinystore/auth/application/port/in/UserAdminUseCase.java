package com.tinystore.auth.application.port.in;

import java.util.Optional;
import java.util.UUID;

import com.tinystore.auth.application.dto.UserStatusView;

public interface UserAdminUseCase {

	void freezeUser(UUID userId);

	void unfreezeUser(UUID userId);

	Optional<UserStatusView> findByPhone(String phone);

	Optional<UserStatusView> getStatus(UUID userId);
}