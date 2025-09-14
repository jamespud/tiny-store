package com.github.spud.tinystore.infrastructure.service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
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

	// 任一商品不存在则失败
	private static final String INCR_SCRIPT =
		"""
			for i=1,#KEYS do
			  local stock = tonumber(redis.call('get', KEYS[i]))
			  if stock == nil then
			    return 0
			  end
			end
			for i=1,#KEYS do
			  redis.call('incrby', KEYS[i], ARGV[i])
			end
			return 1
			""";

	// 任一商品不足则失败
	private static final String DECR_SCRIPT =
		"""
			for i=1,#KEYS do
			  local stock = tonumber(redis.call('get', KEYS[i]))
			  local req = tonumber(ARGV[i])
			  if stock == nil or stock < req then
			    return 0
			  end
			end
			for i=1,#KEYS do
			  redis.call('decrby', KEYS[i], ARGV[i])
			end
			return 1
			""";

	// 任一商品不存在或版本不匹配则失败
	// 版本号加1
	private static final String INCR_BY_VERSION_SCRIPT =
		"""
			local currentVersion = redis.call('get', KEYS[1])
			if not currentVersion or tonumber(currentVersion) >= tonumber(ARGV[1]) then
			    return 0
			end
			redis.call('incrby', KEYS[1], ARGV[1])
			redis.call('incrby', KEYS[2], tonumber(ARGV[2]))
			return 1
			""";

	private static final String DECR_BY_VERSION_SCRIPT =
		"""
			local currentVersion = redis.call('get', KEYS[1])
			if not currentVersion or tonumber(currentVersion) >= tonumber(ARGV[1]) then
			    return 0
			end
			redis.call('incrby', KEYS[1], ARGV[1])
			redis.call('decrby', KEYS[2], tonumber(ARGV[2]))
			return 1
			""";

	private static final RedisScript<Long> BATCH_INCREASE_STOCK_SCRIPT = new DefaultRedisScript<>(
		INCR_SCRIPT, Long.class);
	private static final RedisScript<Long> BATCH_DECREASE_STOCK_SCRIPT = new DefaultRedisScript<>(
		DECR_SCRIPT, Long.class);
	private static final DefaultRedisScript<Long> STOCK_INCR_BY_VERSION_SCRIPT = new DefaultRedisScript<>(
		INCR_BY_VERSION_SCRIPT, Long.class);
	private static final DefaultRedisScript<Long> STOCK_DECR_BY_VERSION_SCRIPT = new DefaultRedisScript<>(
		DECR_BY_VERSION_SCRIPT, Long.class);

	@PostConstruct
	public void init() {
		// 初始化Redis脚本
		redisTemplate.setEnableTransactionSupport(true);
		redisTemplate.setDefaultSerializer(new StringRedisSerializer());
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
		}
		return false;
	}

	public void unlock(String prefix, String target) {
		RLock lock = redisson.getLock(prefix + target);
		if (lock.isHeldByCurrentThread()) {
			lock.unlock();
		}
	}

	/**
	 * 批量增加库存（安全版）
	 *
	 * @param items Map<库存键, 要增加的数量>
	 * @return true 全部增加成功; false 因某个商品不存在而失败
	 */
	public boolean batchIncreaseStock(Map<String, Integer> items) {
		// 1. 准备参数：将Map分离为Key列表和Value列表
		List<String> keys = new ArrayList<>(items.keySet());
		List<String> args = items.values().stream()
			.map(String::valueOf)
			.toList();

		// 2. 执行脚本
		Long result = redisTemplate.execute(
			BATCH_INCREASE_STOCK_SCRIPT,
			keys, // KEYS 列表，例如 ['stock:shop1:sku1', 'stock:shop1:sku2']
			args // ARGV 列表，例如 ['10', '5']
		);

		// 3. 处理结果
		return Long.valueOf(1).equals(result);
	}

	/**
	 * 批量扣减库存（安全版）
	 *
	 * @param items Map<库存键, 要扣减的数量>
	 * @return true 扣减成功; false 库存不足或商品不存在
	 */
	public boolean batchDecreaseStock(Map<String, Integer> items) {
		List<String> keys = new ArrayList<>(items.keySet());
		List<String> args = items.values().stream()
			.map(String::valueOf)
			.toList();

		Long result = redisTemplate.execute(
			BATCH_DECREASE_STOCK_SCRIPT,
			keys, // KEYS 列表
			args // ARGV 列表
		);

		return Long.valueOf(1).equals(result);
	}

	/**
	 * 批量增加库存（使用脚本，外部调用）
	 */
	public boolean batchStockIncr(List<String> keys, List<Integer> quantities) {
		if (keys == null || quantities == null || keys.size() != quantities.size() || keys.isEmpty()) {
			throw new IllegalArgumentException(
				"keys and quantities must be non-null and same non-zero size");
		}
		String[] args = quantities.stream().map(String::valueOf).toArray(String[]::new);
		Long result = redisTemplate.execute(BATCH_INCREASE_STOCK_SCRIPT, keys, (Object[]) args);
		return Long.valueOf(1).equals(result);
	}

	/**
	 * 批量减少库存（使用脚本，外部调用）
	 */
	public boolean batchStockDecr(List<String> keys, List<Integer> quantities) {
		if (keys == null || quantities == null || keys.size() != quantities.size() || keys.isEmpty()) {
			throw new IllegalArgumentException(
				"keys and quantities must be non-null and same non-zero size");
		}
		String[] args = quantities.stream().map(String::valueOf).toArray(String[]::new);
		Long result = redisTemplate.execute(BATCH_DECREASE_STOCK_SCRIPT, keys, (Object[]) args);
		return Long.valueOf(1).equals(result);
	}

	/**
	 * 库存增加（带版本）
	 *
	 * @param totalKey   库存key
	 * @param quantity   数目
	 * @param versionKey 版本key
	 * @param dbVersion  数据库版本
	 * @return true 成功或无需更新; false 更新失败
	 */
	public boolean totalStockIncr(String totalKey, Integer quantity, String versionKey,
		Integer dbVersion) {
		if (!preCheckVersion(versionKey, dbVersion)) {
			return true;
		}
		Long result = redisTemplate.execute(STOCK_INCR_BY_VERSION_SCRIPT,
			List.of(versionKey, totalKey),
			dbVersion.toString(), quantity.toString());
		return Long.valueOf(1).equals(result);
	}

	public boolean totalStockDecr(String totalKey, Integer quantity, String versionKey,
		Integer dbVersion) {
		if (!preCheckVersion(versionKey, dbVersion)) {
			return true;
		}
		Long result = redisTemplate.execute(
			STOCK_DECR_BY_VERSION_SCRIPT,
			List.of(versionKey, totalKey),
			dbVersion.toString(), quantity.toString());
		return Long.valueOf(1).equals(result);
	}

	private boolean preCheckVersion(String versionKey, Integer dbVersion) {
		Object raw = redisTemplate.opsForValue().get(versionKey);
		int cacheVersion = raw == null ? 0 : Integer.parseInt(raw.toString());
		// 版本号相等或缓存版本更大则不更新
		return cacheVersion < dbVersion;
	}

}
