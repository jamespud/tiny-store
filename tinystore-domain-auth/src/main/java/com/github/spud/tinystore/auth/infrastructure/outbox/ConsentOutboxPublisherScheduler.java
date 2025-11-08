package com.github.spud.tinystore.auth.infrastructure.outbox;

import com.github.spud.tinystore.auth.application.config.OutboxProperties;
import com.github.spud.tinystore.auth.application.port.out.ConsentEventPublisherPort;
import com.github.spud.tinystore.auth.application.port.out.OutboxPort;
import com.github.spud.tinystore.auth.domain.event.ConsentChangedEvent;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "tinystore.auth.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ConsentOutboxPublisherScheduler {

  private final OutboxPort outboxPort;
  private final ConsentEventPublisherPort consentPublisherPort;
  private final OutboxProperties outboxProperties;

  public ConsentOutboxPublisherScheduler(OutboxPort outboxPort,
      ConsentEventPublisherPort consentPublisherPort,
      OutboxProperties outboxProperties) {
    this.outboxPort = outboxPort;
    this.consentPublisherPort = consentPublisherPort;
    this.outboxProperties = outboxProperties;
  }

  @Scheduled(fixedDelayString = "#{@outboxProperties.publishInterval.toMillis()}")
  @Transactional
  public void publishOutbox() {
    List<ConsentChangedEvent> events = outboxPort.fetchConsentChangedUnpublished(
        outboxProperties.getBatchSize());
    if (events.isEmpty()) {
      return;
    }
    consentPublisherPort.publish(events);
    outboxPort.markConsentChangedPublished(events);
  }
}