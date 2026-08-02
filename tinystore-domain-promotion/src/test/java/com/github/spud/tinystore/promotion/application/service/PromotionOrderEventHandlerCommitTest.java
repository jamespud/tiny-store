package com.github.spud.tinystore.promotion.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.promotion.infrastructure.kafka.PromotionEventPublisher;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaCheckoutQuoteRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaConsumerEventLogRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaUserCouponRepository;
import com.github.spud.tinystore.promotion.interfaces.dto.ChangeReason;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutResultStatus;

/**
 * PromotionOrderEventHandler.handlePromotionCommit 单元测试：
 * PROMOTION_COMMIT 事件 → 幂等预占 → 回执事件（PROMOTION_COMMITTED / PROMOTION_COMMIT_FAILED）。
 */
@ExtendWith(MockitoExtension.class)
class PromotionOrderEventHandlerCommitTest {

	private static final String CONSUMER_NAME = "promotion-order-consumer";

	@Mock
	private JpaCheckoutQuoteRepository checkoutQuoteRepository;
	@Mock
	private JpaUserCouponRepository userCouponRepository;
	@Mock
	private JpaConsumerEventLogRepository consumerEventLogRepository;
	@Mock
	private CheckoutAppService checkoutAppService;
	@Mock
	private PromotionEventPublisher promotionEventPublisher;

	private PromotionOrderEventHandler handler;

	@BeforeEach
	void setUp() {
		handler = new PromotionOrderEventHandler(
			checkoutQuoteRepository,
			userCouponRepository,
			consumerEventLogRepository,
			new ObjectMapper(),
			checkoutAppService,
			promotionEventPublisher
		);
	}

	@Test
	void handlePromotionCommit_success_shouldPublishCommittedAck() {
		// Given: 首次处理，预占成功
		when(consumerEventLogRepository.existsByEventIdAndConsumerName("evt-1", CONSUMER_NAME))
			.thenReturn(false);
		when(checkoutAppService.commitQuoteCore("q-1", "trade-1", "h"))
			.thenReturn(CheckoutAppService.CommitOutcome.success("q-1", null, List.of()));

		// When
		handler.handlePromotionCommit("evt-1", "trade-1",
			"{\"quoteId\":\"q-1\",\"inputHash\":\"h\",\"traceId\":\"t\"}");

		// Then: 发布 PROMOTION_COMMITTED 回执，payload 含 eventId/tradeId/quoteId/traceId
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
		verify(promotionEventPublisher).publish(eq("PROMOTION_COMMITTED"), eq("trade-1"),
			payloadCaptor.capture(), eq("t"));
		Map<String, Object> ack = payloadCaptor.getValue();
		assertThat(ack).containsEntry("eventType", "PROMOTION_COMMITTED");
		assertThat(ack).containsEntry("tradeId", "trade-1");
		assertThat(ack).containsEntry("quoteId", "q-1");
		assertThat(ack).containsEntry("traceId", "t");
	}

	@Test
	void handlePromotionCommit_failure_shouldPublishFailedAckWithReason() {
		// Given: 首次处理，预占失败（优惠券不可用）
		when(consumerEventLogRepository.existsByEventIdAndConsumerName("evt-2", CONSUMER_NAME))
			.thenReturn(false);
		when(checkoutAppService.commitQuoteCore("q-2", "trade-2", "h2"))
			.thenReturn(CheckoutAppService.CommitOutcome.failure(
				CheckoutResultStatus.REQUOTE_REQUIRED.name(), "优惠券预占失败，请重新报价",
				List.of(ChangeReason.of("COUPON_UNAVAILABLE", "CPN-1"))));

		// When
		handler.handlePromotionCommit("evt-2", "trade-2",
			"{\"quoteId\":\"q-2\",\"inputHash\":\"h2\",\"traceId\":\"t2\"}");

		// Then: 发布 PROMOTION_COMMIT_FAILED 回执，payload 含 reason
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
		verify(promotionEventPublisher).publish(eq("PROMOTION_COMMIT_FAILED"), eq("trade-2"),
			payloadCaptor.capture(), eq("t2"));
		Map<String, Object> ack = payloadCaptor.getValue();
		assertThat(ack).containsEntry("eventType", "PROMOTION_COMMIT_FAILED");
		assertThat(ack).containsEntry("tradeId", "trade-2");
		assertThat(ack).containsEntry("quoteId", "q-2");
		assertThat(ack).containsEntry("reason", "优惠券预占失败，请重新报价");
	}

	@Test
	void handlePromotionCommit_alreadyProcessed_shouldSkip() {
		// Given: 事件已处理过
		when(consumerEventLogRepository.existsByEventIdAndConsumerName("evt-3", CONSUMER_NAME))
			.thenReturn(true);

		// When
		handler.handlePromotionCommit("evt-3", "trade-3", "{\"quoteId\":\"q-3\",\"inputHash\":\"h3\"}");

		// Then: 不执行预占、不发布回执
		verify(checkoutAppService, never()).commitQuoteCore(any(), any(), any());
		verify(promotionEventPublisher, never()).publish(any(), any(), any(), any());
	}
}
