package com.github.spud.tinystore.product.domain.common;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {
    String id();
    Instant occurredAt();
    String type();
}
