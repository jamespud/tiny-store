package com.github.spud.tinystore.auth.domain.event;

import com.github.spud.tinystore.auth.domain.primitives.UserId;
import java.time.OffsetDateTime;

public record UserUnfrozenEvent(UserId userId, OffsetDateTime occurredAt) {

  public static UserUnfrozenEvent of(UserId userId) {
    return new UserUnfrozenEvent(userId, OffsetDateTime.now());
  }
}