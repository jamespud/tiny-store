package com.github.spud.tinystore.auth.domain.event;

import java.time.OffsetDateTime;

import com.github.spud.tinystore.auth.domain.primitives.UserId;

public record UserStatusChangedEvent(UserId userId, String oldStatus, String newStatus, OffsetDateTime occurredAt) {
    
}
