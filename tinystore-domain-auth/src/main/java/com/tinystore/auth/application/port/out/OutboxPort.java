package com.tinystore.auth.application.port.out;

import java.util.List;

import com.tinystore.auth.domain.event.RefreshTokenRevokedEvent;

public interface OutboxPort {

	void save(RefreshTokenRevokedEvent event);

	List<RefreshTokenRevokedEvent> fetchUnpublished(int batchSize);

	void markPublished(List<RefreshTokenRevokedEvent> events);
}