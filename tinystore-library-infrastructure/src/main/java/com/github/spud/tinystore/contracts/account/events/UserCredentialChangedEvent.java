package com.github.spud.tinystore.contracts.account.events;

import java.time.OffsetDateTime;

public record UserCredentialChangedEvent(String userId, Long credentialVersion, OffsetDateTime changeTime) {}

