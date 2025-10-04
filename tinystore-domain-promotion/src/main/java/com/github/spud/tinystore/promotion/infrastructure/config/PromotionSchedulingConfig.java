package com.github.spud.tinystore.promotion.infrastructure.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class PromotionSchedulingConfig {

	public static final int CORE_POOL_SIZE = 5;
	public static final int MAX_POOL_SIZE = 10;
	public static final int QUEUE_CAPACITY = 100;

	// TODO: Declare ThreadPoolTaskScheduler bean with above parameters.
}
