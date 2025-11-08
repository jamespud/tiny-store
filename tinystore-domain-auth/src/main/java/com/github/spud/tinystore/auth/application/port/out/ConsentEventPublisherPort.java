package com.github.spud.tinystore.auth.application.port.out;

import com.github.spud.tinystore.auth.domain.event.ConsentChangedEvent;
import java.util.List;

public interface ConsentEventPublisherPort {

  void publish(List<ConsentChangedEvent> events);
}