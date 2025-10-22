package com.github.spud.tinystore.auth.infrastructure.outbox;

import com.github.spud.tinystore.auth.application.port.out.EventPublisherPort;
import com.github.spud.tinystore.auth.application.port.out.OutboxPort;
import com.github.spud.tinystore.auth.domain.event.RefreshTokenRevokedEvent;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "tinystore.auth.outbox", name = "enabled", havingValue = "false", matchIfMissing = false)
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