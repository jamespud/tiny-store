package com.github.spud.tinystore.promotion.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.github.spud.tinystore.promotion.infrastructure.redis.StockLuaExecutor;

@Configuration
public class PromotionRedisConfig {

	@Bean
	public StockLuaExecutor stockLuaExecutor(StringRedisTemplate redisTemplate) {
		return new StockLuaExecutor(redisTemplate);
	}
}

