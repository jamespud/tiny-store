package com.github.spud.tinystore.auth.application.port.out;

import com.github.spud.tinystore.auth.domain.event.RefreshTokenRevokedEvent;
import java.util.List;

public interface OutboxPort {

  void save(RefreshTokenRevokedEvent event);

  List<RefreshTokenRevokedEvent> fetchUnpublished(int batchSize);

  void markPublished(List<RefreshTokenRevokedEvent> events);
}