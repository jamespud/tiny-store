package com.github.spud.tinystore.order.application.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Outbox 调度器配置
 *
 * @author Spud
 * @date 2025/9/22
 */
@Configuration
@EnableScheduling
@EnableAsync
public class OutboxSchedulerConfig {
	// 启用定时任务和异步执行
}