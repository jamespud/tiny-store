package com.github.spud.tinystore.auth.infrastructure.outbox;

import com.github.spud.tinystore.auth.application.port.out.EventPublisherPort;
import com.github.spud.tinystore.auth.domain.event.RefreshTokenRevokedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "tinystore.auth.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LoggingEventPublisherAdapter implements EventPublisherPort {

	private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisherAdapter.class);

	@Override
	public void publish(List<RefreshTokenRevokedEvent> events) {
		if (events.isEmpty()) {
			return;
		}
		events.forEach(event -> log.info("RefreshTokenRevokedEvent published userId={} rtVersion={} reason={}",
			event.userId().getValue(), event.rtVersion().getValue(), event.reason()));
	}
}
