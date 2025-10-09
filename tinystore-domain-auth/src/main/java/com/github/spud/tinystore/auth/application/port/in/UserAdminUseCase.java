package com.github.spud.tinystore.auth.application.port.in;

import com.github.spud.tinystore.auth.application.dto.UserStatusView;

import java.util.Optional;

public interface UserAdminUseCase {

	void freezeUser(String userId);

	void unfreezeUser(String userId);

	Optional<UserStatusView> findByPhone(String phone);

	Optional<UserStatusView> getStatus(String userId);
}