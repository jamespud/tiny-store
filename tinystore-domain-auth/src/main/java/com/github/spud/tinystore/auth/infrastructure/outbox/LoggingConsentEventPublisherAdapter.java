package com.github.spud.tinystore.auth.infrastructure.outbox;

import com.github.spud.tinystore.auth.application.port.out.ConsentEventPublisherPort;
import com.github.spud.tinystore.auth.domain.event.ConsentChangedEvent;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "tinystore.auth.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LoggingConsentEventPublisherAdapter implements ConsentEventPublisherPort {

  private static final Logger log = LoggerFactory.getLogger(
    LoggingConsentEventPublisherAdapter.class);

  @Override
  public void publish(List<ConsentChangedEvent> events) {
    if (events == null || events.isEmpty()) {
      return;
    }
    events.forEach(evt -> log.info(
      "ConsentChangedEvent published userId={} clientId={} added={} removed={} at={}",
      evt.userId().value(), evt.clientId().value(), evt.addedScopes(), evt.removedScopes(),
      evt.occurredAt()));
  }
}