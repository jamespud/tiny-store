package com.github.spud.tinystore.product.domain.common;

import java.time.Instant;

public interface DomainEvent {

	String id();

	Instant occurredAt();

	String type();
}
