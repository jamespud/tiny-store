package com.github.spud.tinystore.promotion.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.promotion.infrastructure.kafka.PromotionEventPublisher;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.CheckoutQuoteEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaCheckoutQuoteRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaConsumerEventLogRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaUserCouponRepository;
import com.github.spud.tinystore.promotion.interfaces.dto.PricingSnapshot;

@ExtendWith(MockitoExtension.class)
class PromotionOrderEventHandlerTest {

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
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		handler = new PromotionOrderEventHandler(
			checkoutQuoteRepository,
			userCouponRepository,
			consumerEventLogRepository,
			objectMapper,
			checkoutAppService,
			promotionEventPublisher
		);
	}

	@Test
	void handleTradePaid_shouldMarkCouponAsUsed_whenQuoteFound() throws Exception {
		// Given
		String eventId = "evt-001";
		String tradeId = "trade-123";
		String lockId = "plk:abc123";

		// Mock 幂等检查
		when(consumerEventLogRepository.existsByEventIdAndConsumerName(eventId, "promotion-order-consumer"))
			.thenReturn(false);

		// Mock quote 查询
		CheckoutQuoteEntity quoteEntity = new CheckoutQuoteEntity();
		quoteEntity.setId(UUID.randomUUID());
		quoteEntity.setTradeId(tradeId);

		PricingSnapshot.AppliedBenefit benefit = new PricingSnapshot.AppliedBenefit();
		benefit.setBenefitType("PLATFORM_COUPON");
		benefit.setBenefitId("COUPON-001");
		benefit.setLockId(lockId);

		PricingSnapshot snapshot = new PricingSnapshot();
		snapshot.setAppliedBenefits(List.of(benefit));

		CheckoutAppService.CheckoutQuotePayload payload = new CheckoutAppService.CheckoutQuotePayload();
		payload.setSnapshot(snapshot);

		quoteEntity.setSnapshot(objectMapper.writeValueAsString(payload));
		when(checkoutQuoteRepository.findByTradeId(tradeId)).thenReturn(Optional.of(quoteEntity));

		// Mock 优惠券使用
		when(userCouponRepository.useByLockId(eq(lockId), eq(tradeId), any(LocalDateTime.class), any(LocalDateTime.class)))
			.thenReturn(1);

		// When
		handler.handleTradePaid(eventId, tradeId, "{}");

		// Then
		verify(consumerEventLogRepository, times(1)).save(any());
		verify(checkoutQuoteRepository, times(1)).findByTradeId(tradeId);
		verify(userCouponRepository, times(1)).useByLockId(eq(lockId), eq(tradeId), any(LocalDateTime.class),
			any(LocalDateTime.class));
	}

	@Test
	void handleTradePaid_shouldSkip_whenEventAlreadyProcessed() {
		// Given
		String eventId = "evt-002";
		String tradeId = "trade-456";

		when(consumerEventLogRepository.existsByEventIdAndConsumerName(eventId, "promotion-order-consumer"))
			.thenReturn(true);

		// When
		handler.handleTradePaid(eventId, tradeId, "{}");

		// Then
		verify(consumerEventLogRepository, never()).save(any());
		verify(checkoutQuoteRepository, never()).findByTradeId(anyString());
		verify(userCouponRepository, never()).useByLockId(anyString(), anyString(), any(LocalDateTime.class),
			any(LocalDateTime.class));
	}

	@Test
	void handleTradePaid_shouldLogWarning_whenQuoteNotFound() {
		// Given
		String eventId = "evt-003";
		String tradeId = "trade-789";

		when(consumerEventLogRepository.existsByEventIdAndConsumerName(eventId, "promotion-order-consumer"))
			.thenReturn(false);
		when(checkoutQuoteRepository.findByTradeId(tradeId)).thenReturn(Optional.empty());

		// When
		handler.handleTradePaid(eventId, tradeId, "{}");

		// Then
		verify(consumerEventLogRepository, times(1)).save(any());
		verify(checkoutQuoteRepository, times(1)).findByTradeId(tradeId);
		verify(userCouponRepository, never()).useByLockId(anyString(), anyString(), any(LocalDateTime.class),
			any(LocalDateTime.class));
	}

	@Test
	void handleTradeClosed_shouldUnlockCoupon_whenQuoteFound() throws Exception {
		// Given
		String eventId = "evt-004";
		String tradeId = "trade-111";
		String lockId = "plk:xyz789";

		when(consumerEventLogRepository.existsByEventIdAndConsumerName(eventId, "promotion-order-consumer"))
			.thenReturn(false);

		CheckoutQuoteEntity quoteEntity = new CheckoutQuoteEntity();
		quoteEntity.setId(UUID.randomUUID());
		quoteEntity.setTradeId(tradeId);

		PricingSnapshot.AppliedBenefit benefit = new PricingSnapshot.AppliedBenefit();
		benefit.setBenefitType("SHOP_COUPON");
		benefit.setBenefitId("COUPON-002");
		benefit.setLockId(lockId);

		PricingSnapshot snapshot = new PricingSnapshot();
		snapshot.setAppliedBenefits(List.of(benefit));

		CheckoutAppService.CheckoutQuotePayload payload = new CheckoutAppService.CheckoutQuotePayload();
		payload.setSnapshot(snapshot);

		quoteEntity.setSnapshot(objectMapper.writeValueAsString(payload));
		when(checkoutQuoteRepository.findByTradeId(tradeId)).thenReturn(Optional.of(quoteEntity));

		when(userCouponRepository.unlockByLockId(eq(lockId), any(LocalDateTime.class))).thenReturn(1);

		// When
		handler.handleTradeClosed(eventId, tradeId, "{}");

		// Then
		verify(consumerEventLogRepository, times(1)).save(any());
		verify(checkoutQuoteRepository, times(1)).findByTradeId(tradeId);
		verify(userCouponRepository, times(1)).unlockByLockId(eq(lockId), any(LocalDateTime.class));
	}

	@Test
	void handleTradeClosed_shouldSkip_whenEventAlreadyProcessed() {
		// Given
		String eventId = "evt-005";
		String tradeId = "trade-222";

		when(consumerEventLogRepository.existsByEventIdAndConsumerName(eventId, "promotion-order-consumer"))
			.thenReturn(true);

		// When
		handler.handleTradeClosed(eventId, tradeId, "{}");

		// Then
		verify(consumerEventLogRepository, never()).save(any());
		verify(checkoutQuoteRepository, never()).findByTradeId(anyString());
		verify(userCouponRepository, never()).unlockByLockId(anyString(), any(LocalDateTime.class));
	}

	@Test
	void handleTradeClosed_shouldLogWarning_whenQuoteNotFound() {
		// Given
		String eventId = "evt-006";
		String tradeId = "trade-333";

		when(consumerEventLogRepository.existsByEventIdAndConsumerName(eventId, "promotion-order-consumer"))
			.thenReturn(false);
		when(checkoutQuoteRepository.findByTradeId(tradeId)).thenReturn(Optional.empty());

		// When
		handler.handleTradeClosed(eventId, tradeId, "{}");

		// Then
		verify(consumerEventLogRepository, times(1)).save(any());
		verify(checkoutQuoteRepository, times(1)).findByTradeId(tradeId);
		verify(userCouponRepository, never()).unlockByLockId(anyString(), any(LocalDateTime.class));
	}
}
