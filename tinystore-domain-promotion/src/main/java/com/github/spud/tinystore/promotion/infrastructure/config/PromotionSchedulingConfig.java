package com.github.spud.tinystore.promotion.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class PromotionSchedulingConfig {

	public static final int CORE_POOL_SIZE = 5;
	public static final int MAX_POOL_SIZE = 10;
	public static final int QUEUE_CAPACITY = 100;

	@Bean
	public ThreadPoolTaskScheduler promotionTaskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(CORE_POOL_SIZE);
		scheduler.setThreadNamePrefix("promotion-scheduler-");
		scheduler.initialize();
		return scheduler;
	}
}
