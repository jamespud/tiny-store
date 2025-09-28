package com.tinystore.auth.infrastructure.cache.redis;

import com.tinystore.auth.application.port.out.LockAndRateLimitPort;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component
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