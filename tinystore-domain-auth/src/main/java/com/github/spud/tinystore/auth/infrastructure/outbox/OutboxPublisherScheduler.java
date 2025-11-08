package com.github.spud.tinystore.auth.infrastructure.outbox;

import com.github.spud.tinystore.auth.application.config.OutboxProperties;
import com.github.spud.tinystore.auth.application.port.out.EventPublisherPort;
import com.github.spud.tinystore.auth.application.port.out.OutboxPort;
import com.github.spud.tinystore.auth.domain.event.RefreshTokenRevokedEvent;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "tinystore.auth.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisherScheduler {

  private final OutboxPort outboxPort;
  private final EventPublisherPort eventPublisherPort;
  private final OutboxProperties outboxProperties;

  public OutboxPublisherScheduler(OutboxPort outboxPort,
      EventPublisherPort eventPublisherPort,
      OutboxProperties outboxProperties) {
    this.outboxPort = outboxPort;
    this.eventPublisherPort = eventPublisherPort;
    this.outboxProperties = outboxProperties;
  }

  @Scheduled(fixedDelayString = "#{@outboxProperties.publishInterval.toMillis()}")
  @Transactional
  public void publishOutbox() {
    List<RefreshTokenRevokedEvent> events = outboxPort.fetchUnpublished(
        outboxProperties.getBatchSize());
    if (events.isEmpty()) {
      return;
    }
    try {
      eventPublisherPort.publish(events);
      outboxPort.markPublished(events);
    } catch (Exception ex) {
      log.error("Failed to publish outbox events", ex);
      throw ex;
    }
  }
}
