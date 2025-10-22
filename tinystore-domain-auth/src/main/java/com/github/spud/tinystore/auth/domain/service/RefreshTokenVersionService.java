package com.github.spud.tinystore.auth.domain.service;

import com.github.spud.tinystore.auth.domain.event.RefreshTokenRevokedEvent;
import com.github.spud.tinystore.auth.domain.model.user.MallUser;

public class RefreshTokenVersionService {

  public RefreshTokenRevokedEvent revokeAll(MallUser user, String reason) {
    var newVersion = user.bumpRtVersion();
    return RefreshTokenRevokedEvent.of(user.getId(), newVersion, reason);
  }
}