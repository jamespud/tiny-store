package com.github.spud.tinystore.contracts.account.events;

import java.time.OffsetDateTime;

public record UserUpdatedEvent(Long userId, String nickname, String avatarUrl, String extJson,
                               OffsetDateTime updateTime) {}

