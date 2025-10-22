package com.github.spud.tinystore.auth.domain.event;

import com.github.spud.tinystore.auth.domain.primitives.RtVersion;
import com.github.spud.tinystore.auth.domain.primitives.UserId;
import java.time.OffsetDateTime;

public record RefreshTokenRevokedEvent(UserId userId, RtVersion rtVersion, String reason,
                                       OffsetDateTime occurredAt) {

  public static RefreshTokenRevokedEvent of(UserId userId, RtVersion rtVersion, String reason) {
    return new RefreshTokenRevokedEvent(userId, rtVersion, reason, OffsetDateTime.now());
  }
}