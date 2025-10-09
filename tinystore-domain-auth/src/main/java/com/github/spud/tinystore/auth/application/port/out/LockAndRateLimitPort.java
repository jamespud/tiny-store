package com.github.spud.tinystore.auth.application.port.out;

import java.time.Duration;
import java.util.function.Supplier;

public interface LockAndRateLimitPort {

	<T> T withLock(String key, Duration ttl, Supplier<T> action);

	long increment(String key, Duration window);

	void setIfAbsent(String key, String value, Duration ttl);

	String get(String key);
}