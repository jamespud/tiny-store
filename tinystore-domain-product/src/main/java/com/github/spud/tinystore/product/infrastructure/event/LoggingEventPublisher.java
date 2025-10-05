package com.github.spud.tinystore.product.infrastructure.event;

import com.github.spud.tinystore.product.domain.common.DomainEvent;
import com.github.spud.tinystore.product.domain.common.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingEventPublisher implements DomainEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);

	@Override
	public void publish(DomainEvent event) {
		log.info("publish event type={}, id={}, at={}", event.type(), event.id(), event.occurredAt());
	}
}
