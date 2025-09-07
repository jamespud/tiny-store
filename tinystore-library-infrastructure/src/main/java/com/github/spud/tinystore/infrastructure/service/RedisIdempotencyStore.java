package com.github.spud.tinystore.infrastructure.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Redis幂等存储实现
 *
 * @author Spud
 * @date 2025/8/16
 */
@Slf4j
@Component
public class RedisIdempotencyStore {

	@Value("${tinystore.idempotency.ttl-seconds:600}")
	private long idempotencyTtlSeconds;

	@Autowired
	private StringRedisTemplate redisTemplate;

	public String generateIdempotencyKey(String service, String action) {
		return service + ":" + action + ":" + System.currentTimeMillis() + ":" + UUID.randomUUID();
	}

	public boolean verifyIdempotencyKey(String idempotencyKey, String expectedValue) {
		if (!StringUtils.hasText(idempotencyKey)) {
			return false;
		}
		String[] parts = idempotencyKey.split(":");
		if (!(parts.length == 4 && StringUtils.hasText(parts[0]) && StringUtils.hasText(parts[1])
			&& StringUtils.hasText(parts[2]))) {
			return false;
		}
		if (LocalDateTime.parse(parts[2])
			.isAfter(LocalDateTime.now().plus(idempotencyTtlSeconds, ChronoUnit.MILLIS))) {
			return false;
		}
		String existingValue = redisTemplate.opsForValue().get(idempotencyKey);// 检查Redis中是否存在该键
		redisTemplate.opsForValue().getOperations().delete(idempotencyKey); // 删除键，避免重复验证
		if (StringUtils.hasText(existingValue) && existingValue.equals(expectedValue)) {
			return Boolean.TRUE.equals(
				redisTemplate.opsForValue().getOperations().delete(idempotencyKey));
		}
		return false;
	}

	public boolean tryAcquire(String idempotencyKey, String value) {
		try {
			Boolean success = redisTemplate.opsForValue()
				.setIfAbsent(idempotencyKey, value, idempotencyTtlSeconds, TimeUnit.SECONDS);

			if (Boolean.TRUE.equals(success)) {
				return true;
			} else {
				// 键已存在，检查现有值
				String existingValue = redisTemplate.opsForValue().get(idempotencyKey);
				if (StringUtils.hasText(existingValue)) {
					return false;
				} else {
					// 键过期了，重试
					return tryAcquire(idempotencyKey, value);
				}
			}
		} catch (Exception e) {
			log.error("Failed to acquire idempotency lock", e);
			throw new RuntimeException("Failed to acquire idempotency lock", e);
		}
	}
}
