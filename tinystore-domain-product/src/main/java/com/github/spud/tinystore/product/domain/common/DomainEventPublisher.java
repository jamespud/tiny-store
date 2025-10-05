package com.github.spud.tinystore.product.domain.common;

public interface DomainEventPublisher {

	void publish(DomainEvent event);
}
