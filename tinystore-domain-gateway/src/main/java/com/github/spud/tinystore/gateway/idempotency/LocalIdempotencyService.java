package com.github.spud.tinystore.gateway.idempotency;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;

import reactor.core.publisher.Mono;

@Component
public class LocalIdempotencyService {

	private final Cache<String, Instant> cache;

	public LocalIdempotencyService(Cache<String, Instant> cache) {
		this.cache = cache;
	}

	public Mono<IdempotencyResult> tryAcquire(String key, Duration ttl) {
		return Mono.fromSupplier(() -> {
			Instant now = Instant.now();
			Instant previous = cache.getIfPresent(key);
			if (previous != null && previous.isAfter(now)) {
				return IdempotencyResult.duplicate().withFallback();
			}
			cache.put(key, now.plus(ttl));
			return IdempotencyResult.success().withFallback();
		});
	}

	public Mono<Void> markSuccess(String key, Duration ttl) {
		return Mono.fromRunnable(() -> cache.put(key, Instant.now().plus(ttl)));
	}

	public Mono<Void> release(String key) {
		return Mono.fromRunnable(() -> cache.invalidate(key));
	}
}
