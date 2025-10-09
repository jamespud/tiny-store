package com.github.spud.tinystore.auth.application.port.out;

import com.github.spud.tinystore.auth.domain.event.RefreshTokenRevokedEvent;

import java.util.List;

public interface EventPublisherPort {

	void publish(List<RefreshTokenRevokedEvent> events);
}