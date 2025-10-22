package com.github.spud.tinystore.auth.domain.event;

import com.github.spud.tinystore.auth.domain.primitives.UserId;
import java.time.OffsetDateTime;

public record UserFrozenEvent(UserId userId, OffsetDateTime occurredAt) {

  public static UserFrozenEvent of(UserId userId) {
    return new UserFrozenEvent(userId, OffsetDateTime.now());
  }
}