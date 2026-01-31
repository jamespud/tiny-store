package com.github.spud.tinystore.contracts.account.events;

import java.time.OffsetDateTime;

public record UserUpdatedEvent(String userId, String nickname, String avatarUrl, String extJson,
                               OffsetDateTime updateTime) {}

