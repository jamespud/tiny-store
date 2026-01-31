package com.github.spud.tinystore.contracts.account.events;

import java.time.OffsetDateTime;

public record UserCreatedEvent(String userId, String phone, String nickname, OffsetDateTime createTime) {}

