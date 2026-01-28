package com.github.spud.tinystore.auth.domain.event;

import com.github.spud.tinystore.auth.domain.primitives.UserId;
import java.time.OffsetDateTime;

public record UserStatusChangedEvent(UserId userId, String oldStatus, String newStatus,
                                     OffsetDateTime occurredAt) {

}
