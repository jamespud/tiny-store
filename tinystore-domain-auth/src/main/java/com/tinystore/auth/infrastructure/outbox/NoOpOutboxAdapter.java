package com.tinystore.auth.infrastructure.outbox;

import com.tinystore.auth.application.port.out.EventPublisherPort;
import com.tinystore.auth.application.port.out.OutboxPort;
import com.tinystore.auth.domain.event.RefreshTokenRevokedEvent;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class NoOpOutboxAdapter implements OutboxPort, EventPublisherPort {

	@Override
	public void save(RefreshTokenRevokedEvent event) {
		// Phase 1: no-op
	}

	@Override
	public List<RefreshTokenRevokedEvent> fetchUnpublished(int batchSize) {
		return Collections.emptyList();
	}

	@Override
	public void markPublished(List<RefreshTokenRevokedEvent> events) {
		// no-op
	}

	@Override
	public void publish(List<RefreshTokenRevokedEvent> events) {
		// no-op
	}
}