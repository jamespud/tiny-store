package com.github.spud.tinystore.gateway.idempotency;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
public class IdempotencyService {

	private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
	private static final String STATUS_PROCESSING = "PROCESSING";
	private static final String STATUS_COMPLETED = "COMPLETED";

	private final ReactiveStringRedisTemplate redisTemplate;
	private final LocalIdempotencyService localIdempotencyService;
	private final IdempotencyProperties properties;

	public IdempotencyService(ReactiveStringRedisTemplate redisTemplate,
		LocalIdempotencyService localIdempotencyService,
		IdempotencyProperties properties) {
		this.redisTemplate = redisTemplate;
		this.localIdempotencyService = localIdempotencyService;
		this.properties = properties;
	}

	public Mono<IdempotencyResult> tryAcquire(String rawKey) {
		String namespacedKey = namespacedKey(rawKey);
		Duration ttl = properties.getTtl();
		return redisTemplate.opsForValue()
			.setIfAbsent(namespacedKey, STATUS_PROCESSING, ttl)
			.map(acquired -> acquired ? IdempotencyResult.success() : IdempotencyResult.duplicate())
			.switchIfEmpty(Mono.just(IdempotencyResult.duplicate()))
			.onErrorResume(ex -> {
				log.warn("Redis idempotency unavailable, fallback to local cache", ex);
				return localIdempotencyService.tryAcquire(namespacedKey, ttl);
			})
			.defaultIfEmpty(IdempotencyResult.duplicate());
	}

	public Mono<Void> markSuccess(String rawKey) {
		String namespacedKey = namespacedKey(rawKey);
		Duration ttl = properties.getTtl();
		return redisTemplate.opsForValue()
			.set(namespacedKey, STATUS_COMPLETED, ttl)
			.then()
			.onErrorResume(ex -> {
				log.warn("Redis idempotency mark success failed, fallback to local cache", ex);
				return localIdempotencyService.markSuccess(namespacedKey, ttl);
			});
	}

	public Mono<Void> release(String rawKey) {
		String namespacedKey = namespacedKey(rawKey);
		return redisTemplate.delete(namespacedKey)
			.then()
			.onErrorResume(ex -> {
				log.warn("Redis idempotency release failed, fallback to local cache", ex);
				return localIdempotencyService.release(namespacedKey);
			});
	}

	private String namespacedKey(String rawKey) {
		return properties.getKeyPrefix() + ':' + rawKey;
	}
}
