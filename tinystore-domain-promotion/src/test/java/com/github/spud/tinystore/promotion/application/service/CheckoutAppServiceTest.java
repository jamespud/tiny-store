package com.github.spud.tinystore.promotion.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.promotion.infrastructure.idempotency.InMemoryIdempotencyStorage;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaCheckoutQuoteRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaCouponRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaFullReductionCampaignRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaSeckillPriceRuleRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaShippingRuleRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaUserCouponRepository;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutQuoteRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutQuoteResponse;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutResultStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckoutAppServiceTest {

	@Mock
	private JpaCheckoutQuoteRepository checkoutQuoteRepository;
	@Mock
	private JpaCouponRepository couponRepository;
	@Mock
	private JpaUserCouponRepository userCouponRepository;
	@Mock
	private JpaFullReductionCampaignRepository fullReductionCampaignRepository;
	@Mock
	private JpaSeckillPriceRuleRepository seckillPriceRuleRepository;
	@Mock
	private JpaShippingRuleRepository shippingRuleRepository;

	@Test
	void quote_shouldComputeItemsTotalAndPayable_withoutAnyRules() {
		when(fullReductionCampaignRepository.findActive(any())).thenReturn(List.of());
		when(seckillPriceRuleRepository.findActive(any())).thenReturn(List.of());
		when(shippingRuleRepository.findByStatusOrderByVersionDesc("ACTIVE")).thenReturn(List.of());
		when(checkoutQuoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		IdempotencyStorage idempotencyStorage = new InMemoryIdempotencyStorage(600);
		ObjectMapper objectMapper = new ObjectMapper();
		CheckoutAppService svc = new CheckoutAppService(
			idempotencyStorage,
			objectMapper,
			checkoutQuoteRepository,
			couponRepository,
			userCouponRepository,
			fullReductionCampaignRepository,
			seckillPriceRuleRepository,
			shippingRuleRepository,
			10,
			30
		);

		CheckoutQuoteRequest req = new CheckoutQuoteRequest();
		req.setUserId("U1");
		req.setAddressId("A1");
		CheckoutQuoteRequest.Line line = new CheckoutQuoteRequest.Line();
		line.setSkuId("SKU1");
		line.setShopId("S1");
		line.setQuantity(2);
		line.setBaseUnitPriceCents(1000L);
		line.setWeightGrams(10L);
		req.setLines(List.of(line));

		CheckoutQuoteResponse resp = svc.quote("k1", req);
		assertThat(resp.getStatus()).isEqualTo(CheckoutResultStatus.OK);
		assertThat(resp.getQuoteId()).isNotBlank();
		assertThat(resp.getSnapshot().getItemsTotalCents()).isEqualTo(2000L);
		assertThat(resp.getSnapshot().getPayableCents()).isEqualTo(2000L);
		assertThat(resp.getSnapshot().getVersion().getInputHash()).isNotBlank();
	}
}

