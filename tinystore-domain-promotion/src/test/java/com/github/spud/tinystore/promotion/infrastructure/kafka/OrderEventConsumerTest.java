package com.github.spud.tinystore.promotion.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.promotion.application.service.PromotionOrderEventHandler;

/**
 * OrderEventConsumer 事件分发单元测试（重点：PROMOTION_COMMIT 的 tradeId 解析）。
 * order 侧发布约定：PROMOTION_COMMIT 的 aggregateId 为 quoteId，真实 tradeId 在 payload 中。
 */
@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

	@Mock
	private PromotionOrderEventHandler handler;
	@Mock
	private Acknowledgment ack;

	private ObjectMapper objectMapper;
	private OrderEventConsumer consumer;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		consumer = new OrderEventConsumer(handler, objectMapper);
	}

	@Test
	void handleOrderEvent_promotionCommit_shouldPassResolvedTradeIdFromPayload() throws Exception {
		// Given: aggregateId 为 quoteId，tradeId 在 payload 中
		Map<String, Object> env = new HashMap<>();
		env.put("eventId", "evt-1");
		env.put("eventType", "PROMOTION_COMMIT");
		env.put("aggregateType", "PROMOTION");
		env.put("aggregateId", "quote-1");
		String payload = "{\"quoteId\":\"quote-1\",\"tradeId\":\"trade-1\",\"inputHash\":\"h\"}";
		env.put("payload", payload);

		// When
		consumer.handleOrderEvent(objectMapper.writeValueAsString(env), ack);

		// Then: 传入真实 tradeId（而非 aggregateId），并手动 ack
		verify(handler).handlePromotionCommit("evt-1", "trade-1", payload);
		verify(ack).acknowledge();
	}

	@Test
	void handleOrderEvent_promotionCommit_missingTradeId_shouldThrowAndNotAck() throws Exception {
		// Given: payload 缺少 tradeId
		Map<String, Object> env = new HashMap<>();
		env.put("eventId", "evt-2");
		env.put("eventType", "PROMOTION_COMMIT");
		env.put("aggregateType", "PROMOTION");
		env.put("aggregateId", "quote-2");
		String payload = "{\"quoteId\":\"quote-2\",\"inputHash\":\"h\"}";
		env.put("payload", payload);

		// When/Then: 毒消息 → IllegalArgumentException（DLT），不 ack
		assertThatThrownBy(() -> consumer.handleOrderEvent(objectMapper.writeValueAsString(env), ack))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Missing tradeId");
		verifyNoInteractions(handler);
	}
}
