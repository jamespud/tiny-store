package com.tinystore.auth.domain.service;

import com.tinystore.auth.domain.event.RefreshTokenRevokedEvent;
import com.tinystore.auth.domain.model.user.MallUser;

public class RefreshTokenVersionService {

	public RefreshTokenRevokedEvent revokeAll(MallUser user, String reason) {
		var newVersion = user.bumpRtVersion();
		return RefreshTokenRevokedEvent.of(user.getId(), newVersion, reason);
	}
}