package com.github.spud.tinystore.order.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 异步和调度配置
 * 启用异步处理和定时任务功能
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncSchedulingConfig {

	// 这里可以配置自定义的线程池、任务调度器等
	// 暂时使用 Spring 的默认配置
    
    /*
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("order-async-");
        executor.initialize();
        return executor;
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("order-scheduler-");
        scheduler.initialize();
        return scheduler;
    }
    */
}