package com.github.spud.tinystore.auth.domain.event;

import com.github.spud.tinystore.auth.domain.primitives.UserId;
import java.time.OffsetDateTime;

public record UserCreatedEvent(UserId userId, String phone, OffsetDateTime occurredAt) {

}
