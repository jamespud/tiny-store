package com.github.spud.tinystore.inventory.infrastructure.redis;

import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 幂等 operationId -> reservationId 映射缓存
 */
@Component
public class IdempotentReservationCache {

	private final StringRedisTemplate redisTemplate;

	public IdempotentReservationCache(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	public Optional<String> getReservationId(String operationId) {
		if (operationId == null) {
			return Optional.empty();
		}
		String v = redisTemplate.opsForValue().get(RedisKeys.opKey(operationId));
		return Optional.ofNullable(v);
	}

	public void put(String operationId, String reservationId, int ttlSeconds) {
		if (operationId == null) {
			return;
		}
		redisTemplate.opsForValue()
			.set(RedisKeys.opKey(operationId), reservationId, Duration.ofSeconds(ttlSeconds + 60L));
	}
}

