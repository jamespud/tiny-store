package com.github.spud.tinystore.promotion.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.promotion.infrastructure.idempotency.InMemoryIdempotencyStorage;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.CheckoutQuoteEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.SeckillPriceRuleEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaCheckoutQuoteRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaCouponRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaFullReductionCampaignRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaSeckillPriceRuleRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaShippingRuleRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaUserCouponRepository;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutQuoteRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutQuoteResponse;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutCommitRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutCommitResponse;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutResultStatus;
import com.github.spud.tinystore.promotion.interfaces.dto.PricingSnapshot;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
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
			10
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

	@Test
	void quote_shouldReturnIdempotencyConflict_whenPayloadDiffers() {
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
			10
		);

		CheckoutQuoteRequest req1 = new CheckoutQuoteRequest();
		req1.setUserId("U1");
		req1.setAddressId("A1");
		CheckoutQuoteRequest.Line line = new CheckoutQuoteRequest.Line();
		line.setSkuId("SKU1");
		line.setShopId("S1");
		line.setQuantity(1);
		line.setBaseUnitPriceCents(1000L);
		line.setWeightGrams(0L);
		req1.setLines(List.of(line));

		CheckoutQuoteRequest req2 = new CheckoutQuoteRequest();
		req2.setUserId("U1");
		req2.setAddressId("A2");
		req2.setLines(List.of(line));

		CheckoutQuoteResponse r1 = svc.quote("same", req1);
		CheckoutQuoteResponse r2 = svc.quote("same", req2);
		assertThat(r1.getStatus()).isEqualTo(CheckoutResultStatus.OK);
		assertThat(r2.getStatus()).isEqualTo(CheckoutResultStatus.REQUOTE_REQUIRED);
		assertThat(r2.getChangeReasons()).isNotEmpty();
		assertThat(r2.getChangeReasons().get(0).getCode()).isEqualTo("IDEMPOTENCY_CONFLICT");
	}

	@Test
	void commit_shouldRequoteRequired_whenPricingRulesVersionChanged() throws Exception {
		LocalDateTime now = LocalDateTime.now();
		SeckillPriceRuleEntity rule = new SeckillPriceRuleEntity();
		rule.setId(UUID.randomUUID());
		rule.setVersion(2L);
		rule.setScope(null);
		rule.setContent("{\"finalUnitPriceCents\":1000}");
		rule.setStatus("ACTIVE");
		rule.setStartTime(now.minusDays(1));
		rule.setEndTime(now.plusDays(1));
		rule.setCreatedAt(now);
		rule.setUpdatedAt(now);

		when(seckillPriceRuleRepository.findActive(any())).thenReturn(List.of(rule));
		when(fullReductionCampaignRepository.findActive(any())).thenReturn(List.of());
		when(shippingRuleRepository.findByStatusOrderByVersionDesc("ACTIVE")).thenReturn(List.of());
		when(checkoutQuoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		ObjectMapper objectMapper = new ObjectMapper();
		CheckoutAppService.CheckoutQuotePayload payload = new CheckoutAppService.CheckoutQuotePayload();
		CheckoutQuoteRequest quoteReq = new CheckoutQuoteRequest();
		quoteReq.setUserId("U1");
		quoteReq.setAddressId("A1");
		CheckoutQuoteRequest.Line line = new CheckoutQuoteRequest.Line();
		line.setSkuId("SKU1");
		line.setShopId("S1");
		line.setQuantity(1);
		line.setBaseUnitPriceCents(1000L);
		line.setWeightGrams(0L);
		quoteReq.setLines(List.of(line));
		payload.setQuoteRequest(quoteReq);

		PricingSnapshot snap = new PricingSnapshot();
		PricingSnapshot.PricedLine pl = new PricingSnapshot.PricedLine();
		pl.setSkuId("SKU1");
		pl.setShopId("S1");
		pl.setQuantity(1);
		pl.setBaseUnitPriceCents(1000L);
		pl.setFinalUnitPriceCents(1000L);
		pl.setLineSubtotalCents(1000L);
		pl.setLineDiscountAllocatedCents(0L);
		pl.setLinePayableCents(1000L);
		snap.setLines(List.of(pl));
		snap.setItemsTotalCents(1000L);
		snap.setShippingFeeCents(0L);
		snap.setPromotionDiscountTotalCents(0L);
		snap.setCouponDiscountTotalCents(0L);
		snap.setPayableCents(1000L);
		PricingSnapshot.SnapshotVersion ver = new PricingSnapshot.SnapshotVersion();
		ver.setInputHash("IH");
		ver.setPricingRulesVersion("seckill:1|fullReduction:0|couponPolicy:mutexGroup-v1");
		ver.setShippingRulesVersion("shipping:0");
		snap.setVersion(ver);
		payload.setSnapshot(snap);
		payload.setCouponGroupPlans(List.of());

		UUID quoteId = UUID.randomUUID();
		CheckoutQuoteEntity entity = new CheckoutQuoteEntity();
		entity.setId(quoteId);
		entity.setUserId("U1");
		entity.setStatus("QUOTED");
		entity.setInputHash("IH");
		entity.setPricingRulesVersion(ver.getPricingRulesVersion());
		entity.setShippingRulesVersion(ver.getShippingRulesVersion());
		entity.setSnapshot(objectMapper.writeValueAsString(payload));
		entity.setExpiresAt(now.plusMinutes(5));
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now);

		when(checkoutQuoteRepository.findById(eq(quoteId))).thenReturn(Optional.of(entity));

		IdempotencyStorage idempotencyStorage = new InMemoryIdempotencyStorage(600);
		CheckoutAppService svc = new CheckoutAppService(
			idempotencyStorage,
			objectMapper,
			checkoutQuoteRepository,
			couponRepository,
			userCouponRepository,
			fullReductionCampaignRepository,
			seckillPriceRuleRepository,
			shippingRuleRepository,
			10
		);

		CheckoutCommitRequest commitReq = new CheckoutCommitRequest();
		commitReq.setQuoteId(quoteId.toString());
		commitReq.setTradeId("O1");
		commitReq.setInputHash("IH");

		CheckoutCommitResponse resp = svc.commit("c1", commitReq);
		assertThat(resp.getStatus()).isEqualTo(CheckoutResultStatus.REQUOTE_REQUIRED);
		assertThat(resp.getChangeReasons()).isNotEmpty();
		assertThat(resp.getChangeReasons().get(0).getCode()).isEqualTo("SECKILL_PRICE_CHANGED");
		assertThat(resp.getFinalQuoteId()).isNotBlank();
	}
}
