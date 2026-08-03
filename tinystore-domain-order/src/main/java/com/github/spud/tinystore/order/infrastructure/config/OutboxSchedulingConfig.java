package com.github.spud.tinystore.order.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * outbox 发布专用调度器：与默认单线程调度器隔离，
 * 避免超时兜底等长任务饿死 outbox 发布（压测实测停摆根因）。
 * OutboxEventPublisherScheduler 的 @Scheduled 显式引用此 bean。
 */
@Configuration
public class OutboxSchedulingConfig {

    @Bean
    public ThreadPoolTaskScheduler outboxTaskScheduler(
            @Value("${order.outbox.publisher-pool-size:2}") int poolSize) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("outbox-publisher-");
        scheduler.setDaemon(true);
        return scheduler;
    }
}
