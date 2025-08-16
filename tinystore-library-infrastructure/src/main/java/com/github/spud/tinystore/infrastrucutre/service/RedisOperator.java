package com.github.spud.tinystore.infrastrucutre.service;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/8/10
 */
@Service
public class RedisOperator {

	@Autowired
	private RedisTemplate<String, Object> redisTemplate;

	@Autowired
	private RedissonClient redisson;

	private static final String incrScript =
		"for i=1,#KEYS do " +
			"  local stock = tonumber(redis.call('get', KEYS[i])) " +
			"  if stock == nil then " +
			"    return 0 " + // 任一商品不存在则失败
			"  end " +
			"end " +
			"for i=1,#KEYS do " +
			"  redis.call('incrby', KEYS[i], ARGV[i]) " +
			"end " +
			"return 1";

	private static final String decrScript =
		"for i=1,#KEYS do " +
			"  local stock = tonumber(redis.call('get', KEYS[i])) " +
			"  local req = tonumber(ARGV[i]) " +
			"  if stock == nil or stock < req then " +
			"    return 0 " + // 任一商品不足则失败
			"  end " +
			"end " +
			"for i=1,#KEYS do " +
			"  redis.call('decrby', KEYS[i], ARGV[i]) " +
			"end " +
			"return 1";

	private DefaultRedisScript<Long> stockDecrScript;
	private DefaultRedisScript<Long> stockIncrScript;

	@PostConstruct
	public void init() {
		// 初始化Redis脚本
		stockIncrScript = new DefaultRedisScript<>(incrScript, Long.class);
		stockDecrScript = new DefaultRedisScript<>(decrScript, Long.class);
		redisTemplate.setEnableTransactionSupport(true);
		redisTemplate.setDefaultSerializer(new StringRedisSerializer());
	}

	public boolean tryLock(String prefix, String target, long leaseTime) {
		return tryLock(prefix, target, 0, leaseTime, TimeUnit.SECONDS);
	}

	public boolean tryLock(String prefix, String target, long waitTime, long leaseTime, TimeUnit unit) {
		RLock lock = redisson.getLock(prefix + target);
		try {
			return lock.tryLock(waitTime, leaseTime, unit);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	public void unlock(String prefix, String target) {
		RLock lock = redisson.getLock(prefix + target);
		if (lock.isHeldByCurrentThread()) {
			lock.unlock();
		}
	}
}
