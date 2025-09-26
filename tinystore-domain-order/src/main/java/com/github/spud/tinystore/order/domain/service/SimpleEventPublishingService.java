package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.domain.model.Outbox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 事件发布服务的简单实现
 * 目前仅记录日志，实际生产环境中应该发送到消息队列
 *
 * @author Spud
 * @date 2025/9/22
 */
@Slf4j
@Service
public class SimpleEventPublishingService implements EventPublishingService {

	@Override
	public void publish(Outbox event) throws Exception {
		// 简单实现：仅记录日志
		// 生产环境中应该发送到 RabbitMQ、Kafka 等消息队列
		log.info("Publishing event to external system: eventId={}, eventType={}, aggregateId={}, payload={}",
			event.getEventId(), event.getEventType(), event.getAggregateId(), event.getPayload());

		// 模拟网络延迟
		Thread.sleep(10);

		// 模拟偶发的发布失败（用于测试重试机制）
		if (Math.random() < 0.1) { // 10% 失败率
			throw new RuntimeException("Simulated publish failure for testing");
		}

		log.debug("Event published successfully: eventId={}", event.getEventId());
	}
}