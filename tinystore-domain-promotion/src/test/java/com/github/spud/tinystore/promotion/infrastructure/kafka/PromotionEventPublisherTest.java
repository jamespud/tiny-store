package com.github.spud.tinystore.promotion.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * PromotionEventPublisher 序列化行为测试：
 * publish 需把 eventType/traceId/timestamp 与 payload 扁平合并后 JSON 序列化发送。
 */
@ExtendWith(MockitoExtension.class)
class PromotionEventPublisherTest {

	@Mock
	private KafkaTemplate<String, String> kafkaTemplate;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void publish_shouldSendJsonWithEnvelopeFields_toConfiguredTopic() throws Exception {
		// Given
		PromotionEventPublisher publisher = new PromotionEventPublisher(
			kafkaTemplate, objectMapper, "tinystore.promotion.general");

		Map<String, Object> payload = new HashMap<>();
		payload.put("eventId", "ack-1");
		payload.put("tradeId", "trade-1");
		payload.put("quoteId", "q-1");

		// When
		publisher.publish("PROMOTION_COMMITTED", "trade-1", payload, "trace-1");

		// Then: 发送到配置 topic，value 为含 eventType/traceId/timestamp 的扁平 JSON
		ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
		verify(kafkaTemplate).send(eq("tinystore.promotion.general"), eq("trade-1"), jsonCaptor.capture());

		@SuppressWarnings("unchecked")
		Map<String, Object> sent = objectMapper.readValue(jsonCaptor.getValue(), Map.class);
		assertThat(sent).containsEntry("eventType", "PROMOTION_COMMITTED");
		assertThat(sent).containsEntry("traceId", "trace-1");
		assertThat(sent).containsEntry("eventId", "ack-1");
		assertThat(sent).containsEntry("tradeId", "trade-1");
		assertThat(sent).containsEntry("quoteId", "q-1");
		assertThat(sent).containsKey("timestamp");
	}

	@Test
	void publish_shouldThrowIllegalStateException_whenSerializationFails() {
		// Given: payload 含 Jackson 无法序列化的对象（循环引用 → JsonMappingException）
		PromotionEventPublisher publisher = new PromotionEventPublisher(
			kafkaTemplate, objectMapper, "tinystore.promotion.general");

		Map<String, Object> payload = new HashMap<>();
		payload.put("eventId", "ack-2");
		Map<String, Object> cyclic = new HashMap<>();
		cyclic.put("self", cyclic);
		payload.put("badValue", cyclic);

		// When/Then: 序列化失败包装为 IllegalStateException
		assertThatThrownBy(() -> publisher.publish("PROMOTION_COMMIT_FAILED", "trade-2", payload, "trace-2"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Failed to publish promotion event: PROMOTION_COMMIT_FAILED");
	}

	@Test
	void publish_withActiveTransaction_shouldDeferSendUntilAfterCommit() throws Exception {
		// Given: 事务同步激活
		TransactionSynchronizationManager.initSynchronization();
		try {
			PromotionEventPublisher publisher = new PromotionEventPublisher(
				kafkaTemplate, objectMapper, "tinystore.promotion.general");

			Map<String, Object> payload = new HashMap<>();
			payload.put("eventId", "ack-3");
			payload.put("tradeId", "trade-3");

			// When: 事务内发布
			publisher.publish("PROMOTION_COMMITTED", "trade-3", payload, "trace-3");

			// Then: 提交前未发送，注册了 afterCommit 同步
			org.mockito.Mockito.verifyNoInteractions(kafkaTemplate);
			assertThat(TransactionSynchronizationManager.getSynchronizations()).isNotEmpty();

			// 提交（触发 afterCommit 回调）
			TransactionSynchronizationManager.getSynchronizations()
				.forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

			// 提交后发送
			verify(kafkaTemplate).send(eq("tinystore.promotion.general"), eq("trade-3"),
				org.mockito.ArgumentMatchers.contains("PROMOTION_COMMITTED"));
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}
}
