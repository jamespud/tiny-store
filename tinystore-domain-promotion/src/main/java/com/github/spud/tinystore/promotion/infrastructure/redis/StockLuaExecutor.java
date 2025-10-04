package com.github.spud.tinystore.promotion.infrastructure.redis;

import java.util.Collections;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

public class StockLuaExecutor {

	private final StringRedisTemplate redisTemplate;
	private final DefaultRedisScript<Long> decrementScript;
	private final DefaultRedisScript<Long> incrementScript;

	public StockLuaExecutor(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
		this.decrementScript = new DefaultRedisScript<>();
		this.decrementScript.setResultType(Long.class);
		this.decrementScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/stock_decr.lua")));
		this.incrementScript = new DefaultRedisScript<>();
		this.incrementScript.setResultType(Long.class);
		this.incrementScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/stock_incr.lua")));
	}

	public Long decrementStock(String couponId, long amount) {
		// TODO: Execute Lua script using redisTemplate.
		return redisTemplate.execute(decrementScript, Collections.singletonList(PromotionRedisKeys.stock(couponId)), couponId, String.valueOf(amount));
	}

	public Long incrementStock(String couponId, long amount) {
		// TODO: Execute Lua script using redisTemplate.
		return redisTemplate.execute(incrementScript, Collections.singletonList(PromotionRedisKeys.stock(couponId)), couponId, String.valueOf(amount));
	}
}
