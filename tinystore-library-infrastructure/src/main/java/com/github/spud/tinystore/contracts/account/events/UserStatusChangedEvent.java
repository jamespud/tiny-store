package com.github.spud.tinystore.contracts.account.events;

import java.time.OffsetDateTime;

public record UserStatusChangedEvent(Long userId, Integer oldStatus, Integer newStatus,
                                     OffsetDateTime changeTime) {}

