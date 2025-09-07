package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.infrastructure.common.constant.MessageTopicConfig;
import com.github.spud.tinystore.order.interfaces.dto.Item;
import com.github.spud.tinystore.order.interfaces.dto.Settlement;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/8/5
 */
@Service
public class OrderRedisOperatorService {

	@Autowired
	private StringRedisTemplate redisTemplate;

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

	public boolean reduceStock(Settlement bill) {
		List<String> productIds = bill.getItems().stream()
			.map(Item::getProductId)
			.map(id -> MessageTopicConfig.STOCK_KEY_PREFIX + id)
			.collect(Collectors.toList());

		List<Integer> quantities = bill.getItems().stream()
			.map(Item::getAmount) // 提取商品数量
			.collect(Collectors.toList());
		return batchPreDeductStock(productIds, quantities);
	}

	public boolean revertStock(Settlement bill) {
		List<String> productIds = bill.getItems().stream()
			.map(Item::getProductId)
			.map(id -> MessageTopicConfig.STOCK_KEY_PREFIX + id)
			.collect(Collectors.toList());

		List<Integer> quantities = bill.getItems().stream()
			.map(Item::getAmount) // 提取商品数量
			.collect(Collectors.toList());
		return batchRestoreStock(productIds, quantities);
	}

	private boolean batchPreDeductStock(List<String> stockKeys, List<Integer> amounts) {
		Long result = redisTemplate.execute(
			stockDecrScript,
			stockKeys,
			amounts.toArray()
		);
		return result == 1;
	}

	private boolean batchRestoreStock(List<String> stockKeys, List<Integer> amounts) {
		Long result = redisTemplate.execute(
			stockIncrScript,
			stockKeys,
			amounts.toArray()
		);
		return result == 1;
	}

	public boolean tryLock(String prefix, String target, long leaseTime) {
		return tryLock(prefix, target, 0, leaseTime, TimeUnit.SECONDS);
	}

	public boolean tryLock(String prefix, String target, long waitTime, long leaseTime,
		TimeUnit unit) {
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

