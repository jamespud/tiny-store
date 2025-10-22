package com.github.spud.tinystore.auth.infrastructure.cache.redis;

import com.github.spud.tinystore.auth.application.port.out.LockAndRateLimitPort;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(LockAndRateLimitPort.class)
public class NoOpLockAndRateLimitAdapter implements LockAndRateLimitPort {

  @Override
  public <T> T withLock(String key, Duration ttl, Supplier<T> action) {
    return action.get();
  }

  @Override
  public long increment(String key, Duration window) {
    return 1;
  }

  @Override
  public void setIfAbsent(String key, String value, Duration ttl) {
    // no-op
  }

  @Override
  public String get(String key) {
    return null;
  }
}