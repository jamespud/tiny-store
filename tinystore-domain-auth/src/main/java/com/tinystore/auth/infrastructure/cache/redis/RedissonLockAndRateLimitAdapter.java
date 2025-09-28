package com.tinystore.auth.infrastructure.cache.redis;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.tinystore.auth.application.port.out.LockAndRateLimitPort;

@Component
@Primary
@ConditionalOnBean(RedissonClient.class)
public class RedissonLockAndRateLimitAdapter implements LockAndRateLimitPort {

	private final RedissonClient redissonClient;
	private final StringRedisTemplate redisTemplate;

	public RedissonLockAndRateLimitAdapter(RedissonClient redissonClient, StringRedisTemplate redisTemplate) {
		this.redissonClient = redissonClient;
		this.redisTemplate = redisTemplate;
	}

	@Override
	public <T> T withLock(String key, Duration ttl, Supplier<T> action) {
		RLock lock = redissonClient.getLock(key);
		boolean locked = false;
		try {
			locked = lock.tryLock(0, ttl.toMillis(), TimeUnit.MILLISECONDS);
			if (!locked) {
				throw new IllegalStateException("Failed to acquire lock for key " + key);
			}
			return action.get();
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while acquiring lock for key " + key, ex);
		} finally {
			if (locked && lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		}
	}

	@Override
	public long increment(String key, Duration window) {
		Long value = redisTemplate.opsForValue().increment(key);
		if (value != null && value == 1L && window != null) {
			redisTemplate.expire(key, window);
		}
		return value != null ? value : 0L;
	}

	@Override
	public void setIfAbsent(String key, String value, Duration ttl) {
		if (ttl != null) {
			redisTemplate.opsForValue().setIfAbsent(key, value, ttl);
		} else {
			redisTemplate.opsForValue().setIfAbsent(key, value);
		}
	}

	@Override
	public String get(String key) {
		return redisTemplate.opsForValue().get(key);
	}
}
