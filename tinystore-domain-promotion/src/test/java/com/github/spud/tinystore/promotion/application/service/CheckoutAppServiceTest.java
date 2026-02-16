package com.github.spud.tinystore.promotion.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.promotion.infrastructure.idempotency.InMemoryIdempotencyStorage;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.CheckoutQuoteEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.CouponEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.SeckillPriceRuleEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.UserCouponEntity;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
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
	void quote_shouldNotLockCoupon_andLockIdShouldBeNull() {
		when(fullReductionCampaignRepository.findActive(any())).thenReturn(List.of());
		when(seckillPriceRuleRepository.findActive(any())).thenReturn(List.of());
		when(shippingRuleRepository.findByStatusOrderByVersionDesc("ACTIVE")).thenReturn(List.of());
		when(checkoutQuoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		CouponEntity coupon = new CouponEntity();
		UUID couponId = UUID.randomUUID();
		coupon.setId(couponId);
		coupon.setCouponNo("CPN-1");
		coupon.setCouponType("CASH");
		coupon.setScopeType("PLATFORM");
		coupon.setShopId(null);
		coupon.setStatus("ACTIVE");
		coupon.setThresholdAmount(BigDecimal.ZERO);
		coupon.setDiscountAmount(BigDecimal.valueOf(100));
		coupon.setTotalStock(1);
		coupon.setUsedStock(0);
		coupon.setStartTime(LocalDateTime.now().minusDays(1));
		coupon.setEndTime(LocalDateTime.now().plusDays(1));
		coupon.setPriority(1);
		coupon.setCreatedAt(LocalDateTime.now().minusDays(1));
		coupon.setUpdatedAt(LocalDateTime.now());
		when(couponRepository.findByCouponNo("CPN-1")).thenReturn(Optional.of(coupon));

		UserCouponEntity userCoupon = new UserCouponEntity();
		userCoupon.setId(UUID.randomUUID());
		when(userCouponRepository.findFirstByUserIdAndCouponIdAndUseStatusOrderByReceiveTimeAsc("U1", couponId, "UNUSED"))
			.thenReturn(Optional.of(userCoupon));

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
		line.setQuantity(1);
		line.setBaseUnitPriceCents(1000L);
		line.setWeightGrams(0L);
		req.setLines(List.of(line));
		CheckoutQuoteRequest.AppliedIntent intent = new CheckoutQuoteRequest.AppliedIntent();
		intent.setPlatformCouponIds(List.of("CPN-1"));
		intent.setShopCouponIdsByShop(Map.of());
		req.setAppliedIntent(intent);

		CheckoutQuoteResponse resp = svc.quote("k-lockless", req);
		assertThat(resp.getSnapshot().getAppliedBenefits()).isNotEmpty();
		PricingSnapshot.AppliedBenefit benefit = resp.getSnapshot().getAppliedBenefits().get(0);
		assertThat(benefit.getBenefitId()).isEqualTo("CPN-1");
		assertThat(benefit.getLockId()).isNull();

		verify(userCouponRepository, never()).lockUnused(any(), anyString(), any(), any());
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

	@Test
	void commit_shouldLockCoupon_andReplayWhenPayFieldsChange() throws Exception {
		LocalDateTime now = LocalDateTime.now();
		when(seckillPriceRuleRepository.findActive(any())).thenReturn(List.of());
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
		snap.setCouponDiscountTotalCents(100L);
		snap.setPayableCents(900L);

		PricingSnapshot.AppliedBenefit benefit = new PricingSnapshot.AppliedBenefit();
		benefit.setBenefitType("PLATFORM_COUPON");
		benefit.setBenefitId("CPN-1");
		benefit.setGroupKey("PLATFORM:DEFAULT");
		benefit.setAmountCents(-100L);
		snap.setAppliedBenefits(List.of(benefit));

		PricingSnapshot.SnapshotVersion ver = new PricingSnapshot.SnapshotVersion();
		ver.setInputHash("IH");
		ver.setPricingRulesVersion("seckill:0|fullReduction:0|couponPolicy:mutexGroup-v1");
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

		CouponEntity coupon = new CouponEntity();
		UUID couponId = UUID.randomUUID();
		coupon.setId(couponId);
		coupon.setCouponNo("CPN-1");
		UserCouponEntity userCoupon = new UserCouponEntity();
		userCoupon.setId(UUID.randomUUID());

		when(checkoutQuoteRepository.findById(eq(quoteId))).thenReturn(Optional.of(entity));
		when(couponRepository.findByCouponNo("CPN-1")).thenReturn(Optional.of(coupon));
		when(userCouponRepository.findFirstByUserIdAndCouponIdAndUseStatusForUpdate("U1", couponId, "UNUSED"))
			.thenReturn(Optional.of(userCoupon));
		when(userCouponRepository.lockUnused(eq(userCoupon.getId()), anyString(), any(), any())).thenReturn(1);

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

		CheckoutCommitRequest first = new CheckoutCommitRequest();
		first.setQuoteId(quoteId.toString());
		first.setTradeId("T1");
		first.setInputHash("IH");

		CheckoutCommitRequest second = new CheckoutCommitRequest();
		second.setQuoteId(quoteId.toString());
		second.setTradeId("T1");
		second.setInputHash("IH");
		second.setPayNo("P-1");
		second.setPaidAt(System.currentTimeMillis());

		CheckoutCommitResponse firstResp = svc.commit("same-key", first);
		CheckoutCommitResponse secondResp = svc.commit("same-key", second);

		assertThat(firstResp.getStatus()).isEqualTo(CheckoutResultStatus.OK);
		assertThat(secondResp.getStatus()).isEqualTo(CheckoutResultStatus.OK);
		verify(userCouponRepository, times(1)).lockUnused(eq(userCoupon.getId()), anyString(), any(), any());
	}

	@Test
	void release_shouldUnlockCoupon_whenQuoteCommitted() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();
		CheckoutAppService.CheckoutQuotePayload payload = new CheckoutAppService.CheckoutQuotePayload();
		CheckoutQuoteRequest quoteReq = new CheckoutQuoteRequest();
		quoteReq.setUserId("U1");
		quoteReq.setAddressId("A1");
		payload.setQuoteRequest(quoteReq);

		PricingSnapshot snapshot = new PricingSnapshot();
		PricingSnapshot.AppliedBenefit benefit = new PricingSnapshot.AppliedBenefit();
		benefit.setBenefitType("PLATFORM_COUPON");
		benefit.setBenefitId("CPN-1");
		benefit.setLockId("plk:abc123");
		benefit.setAmountCents(-100L);
		snapshot.setAppliedBenefits(List.of(benefit));
		payload.setSnapshot(snapshot);
		payload.setCouponGroupPlans(List.of());

		UUID quoteId = UUID.randomUUID();
		CheckoutQuoteEntity entity = new CheckoutQuoteEntity();
		entity.setId(quoteId);
		entity.setStatus("COMMITTED");
		entity.setSnapshot(objectMapper.writeValueAsString(payload));

		when(checkoutQuoteRepository.findById(eq(quoteId))).thenReturn(Optional.of(entity));
		when(userCouponRepository.unlockByLockId(eq("plk:abc123"), any())).thenReturn(1);

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

		com.github.spud.tinystore.promotion.interfaces.dto.CheckoutReleaseRequest release =
			new com.github.spud.tinystore.promotion.interfaces.dto.CheckoutReleaseRequest();
		release.setQuoteId(quoteId.toString());
		release.setTradeId("T1");
		release.setReason("MANUAL");

		svc.release("r1", release);

		verify(userCouponRepository, times(1)).unlockByLockId(eq("plk:abc123"), any());
		verify(checkoutQuoteRepository, times(1)).updateStatus(eq(quoteId), eq("COMMITTED"), eq("RELEASED"), any());
	}
}
