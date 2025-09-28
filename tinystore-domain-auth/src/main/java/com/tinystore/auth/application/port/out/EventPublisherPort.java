package com.tinystore.auth.application.port.out;

import java.util.List;

import com.tinystore.auth.domain.event.RefreshTokenRevokedEvent;

public interface EventPublisherPort {

	void publish(List<RefreshTokenRevokedEvent> events);
}