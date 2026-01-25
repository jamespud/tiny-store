package com.github.spud.tinystore.contracts.account.events;

import java.time.OffsetDateTime;

public record UserCredentialChangedEvent(Long userId, Long credentialVersion, OffsetDateTime changeTime) {}

