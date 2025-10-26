package com.github.spud.tinystore.auth.application.port.out;

import com.github.spud.tinystore.auth.domain.event.ConsentChangedEvent;
import com.github.spud.tinystore.auth.domain.event.RefreshTokenRevokedEvent;
import com.github.spud.tinystore.auth.domain.event.UserFrozenEvent;
import com.github.spud.tinystore.auth.domain.event.UserUnfrozenEvent;
import java.util.List;

public interface OutboxPort {

  void save(RefreshTokenRevokedEvent event);
	
	void save(ConsentChangedEvent event);
	
	void save(UserFrozenEvent event);
	
	void save(UserUnfrozenEvent event);

  List<RefreshTokenRevokedEvent> fetchUnpublished(int batchSize);

  void markPublished(List<RefreshTokenRevokedEvent> events);
}