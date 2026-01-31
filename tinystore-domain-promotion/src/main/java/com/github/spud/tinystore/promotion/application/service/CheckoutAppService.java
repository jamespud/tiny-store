package com.github.spud.tinystore.promotion.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.spud.tinystore.promotion.interfaces.dto.ChangeReason;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutCommitRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutCommitResponse;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutQuoteRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutQuoteResponse;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutReleaseRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutReleaseResponse;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutResultStatus;
import com.github.spud.tinystore.promotion.interfaces.dto.PricingSnapshot;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.CheckoutQuoteEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.CouponEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.FullReductionCampaignEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.SeckillPriceRuleEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.ShippingRuleEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.UserCouponEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaCheckoutQuoteRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaCouponRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaFullReductionCampaignRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaSeckillPriceRuleRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaShippingRuleRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaUserCouponRepository;

@Service
public class CheckoutAppService {

	private final IdempotencyStorage idempotencyStorage;
	private final ObjectMapper objectMapper;
	private final JpaCheckoutQuoteRepository checkoutQuoteRepository;
	private final JpaCouponRepository couponRepository;
	private final JpaUserCouponRepository userCouponRepository;
	private final JpaFullReductionCampaignRepository fullReductionCampaignRepository;
	private final JpaSeckillPriceRuleRepository seckillPriceRuleRepository;
	private final JpaShippingRuleRepository shippingRuleRepository;
	private final long quoteTtlSeconds;
	private final long couponLockTimeoutMinutes;

	public CheckoutAppService(IdempotencyStorage idempotencyStorage,
		ObjectMapper objectMapper,
		JpaCheckoutQuoteRepository checkoutQuoteRepository,
		JpaCouponRepository couponRepository,
		JpaUserCouponRepository userCouponRepository,
		JpaFullReductionCampaignRepository fullReductionCampaignRepository,
		JpaSeckillPriceRuleRepository seckillPriceRuleRepository,
		JpaShippingRuleRepository shippingRuleRepository,
		@Value("${promotion.checkout.quote-ttl-minutes:10}") long quoteTtlMinutes,
		@Value("${promotion.coupon.lock-timeout-minutes:30}") long couponLockTimeoutMinutes) {
		this.idempotencyStorage = idempotencyStorage;
		this.objectMapper = objectMapper;
		this.checkoutQuoteRepository = checkoutQuoteRepository;
		this.couponRepository = couponRepository;
		this.userCouponRepository = userCouponRepository;
		this.fullReductionCampaignRepository = fullReductionCampaignRepository;
		this.seckillPriceRuleRepository = seckillPriceRuleRepository;
		this.shippingRuleRepository = shippingRuleRepository;
		this.quoteTtlSeconds = quoteTtlMinutes * 60;
		this.couponLockTimeoutMinutes = couponLockTimeoutMinutes;
	}

	@Transactional
	public CheckoutQuoteResponse quote(String idempotencyKey, CheckoutQuoteRequest request) {
		String key = "promotion:checkout:quote:" + idempotencyKey;
		String requestHash = computeInputHash(request);
		IdempotencyStorage.StoredValue stored = idempotencyStorage.get(key);
		if (stored != null) {
			if (!Objects.equals(requestHash, stored.requestHash())) {
				CheckoutQuoteResponse conflict = new CheckoutQuoteResponse();
				conflict.setStatus(CheckoutResultStatus.REQUOTE_REQUIRED);
				conflict.setQuoteId(null);
				conflict.setExpiresAtEpochMs(0L);
				conflict.setSnapshot(null);
				conflict.setChangeReasons(List.of(ChangeReason.of("IDEMPOTENCY_CONFLICT", null)));
				return conflict;
			}
			Object v = stored.value();
			if (v instanceof CheckoutQuoteResponse r) {
				return r;
			}
		}
		CheckoutQuoteResponse resp = createQuote(request);
		idempotencyStorage.put(key, requestHash, resp);
		return resp;
	}

	@Transactional
	public CheckoutCommitResponse commit(String idempotencyKey, CheckoutCommitRequest request) {
		String key = "promotion:checkout:commit:" + idempotencyKey;
		String requestHash = sha256(String.valueOf(request.getQuoteId()) + "|" + String.valueOf(request.getTradeId()) + "|"
			+ String.valueOf(request.getInputHash()) + "|" + String.valueOf(request.getPayNo()) + "|" + String.valueOf(request.getPaidAt()));
		IdempotencyStorage.StoredValue stored = idempotencyStorage.get(key);
		if (stored != null) {
			if (!Objects.equals(requestHash, stored.requestHash())) {
				CheckoutCommitResponse conflict = new CheckoutCommitResponse();
				conflict.setStatus(CheckoutResultStatus.REQUOTE_REQUIRED);
				conflict.setFinalQuoteId(null);
				conflict.setSnapshot(null);
				conflict.setChangeReasons(List.of(ChangeReason.of("IDEMPOTENCY_CONFLICT", null)));
				conflict.setMessage("IDEMPOTENCY_CONFLICT");
				return conflict;
			}
			Object v = stored.value();
			if (v instanceof CheckoutCommitResponse r) {
				return r;
			}
		}

		CheckoutCommitResponse resp = new CheckoutCommitResponse();
		UUID quoteId;
		try {
			quoteId = UUID.fromString(request.getQuoteId());
		} catch (Exception e) {
			resp.setStatus(CheckoutResultStatus.REQUOTE_REQUIRED);
			resp.setFinalQuoteId(null);
			resp.setSnapshot(null);
			resp.setChangeReasons(List.of(ChangeReason.of("QUOTE_NOT_FOUND", null)));
			resp.setMessage("需要重新报价");
			idempotencyStorage.put(key, requestHash, resp);
			return resp;
		}

		Optional<CheckoutQuoteEntity> opt = checkoutQuoteRepository.findById(quoteId);
		if (opt.isEmpty()) {
			resp.setStatus(CheckoutResultStatus.REQUOTE_REQUIRED);
			resp.setFinalQuoteId(null);
			resp.setSnapshot(null);
			resp.setChangeReasons(List.of(ChangeReason.of("QUOTE_NOT_FOUND", null)));
			resp.setMessage("需要重新报价");
			idempotencyStorage.put(key, requestHash, resp);
			return resp;
		}
		CheckoutQuoteEntity entity = opt.get();
		CheckoutQuotePayload payload = readPayload(entity.getSnapshot());

		LocalDateTime now = LocalDateTime.now();
		if (entity.getExpiresAt().isBefore(now)) {
			releaseLocks(payload, now);
			checkoutQuoteRepository.updateStatus(entity.getId(), entity.getStatus(), "EXPIRED", now);
			CheckoutQuoteResponse newQuote = createQuote(payload.getQuoteRequest());
			resp.setStatus(CheckoutResultStatus.REQUOTE_REQUIRED);
			resp.setFinalQuoteId(newQuote.getQuoteId());
			resp.setSnapshot(newQuote.getSnapshot());
			resp.setChangeReasons(List.of(ChangeReason.of("QUOTE_EXPIRED", null)));
			resp.setMessage("需要重新报价");
			idempotencyStorage.put(key, requestHash, resp);
			return resp;
		}

		PricingSnapshot snapshot = payload.getSnapshot();
		if (snapshot.getVersion() == null || !Objects.equals(request.getInputHash(), snapshot.getVersion().getInputHash())) {
			CheckoutQuoteResponse newQuote = createQuote(payload.getQuoteRequest());
			resp.setStatus(CheckoutResultStatus.REQUOTE_REQUIRED);
			resp.setFinalQuoteId(newQuote.getQuoteId());
			resp.setSnapshot(newQuote.getSnapshot());
			resp.setChangeReasons(List.of(ChangeReason.of("INPUT_CHANGED", null)));
			resp.setMessage("需要重新报价");
			idempotencyStorage.put(key, requestHash, resp);
			return resp;
		}

		String currentPricingVersion = computePricingRulesVersion(now);
		String currentShippingVersion = computeShippingRulesVersion();
		if (!Objects.equals(currentPricingVersion, snapshot.getVersion().getPricingRulesVersion())) {
			CheckoutQuoteResponse newQuote = createQuote(payload.getQuoteRequest());
			resp.setStatus(CheckoutResultStatus.REQUOTE_REQUIRED);
			resp.setFinalQuoteId(newQuote.getQuoteId());
			resp.setSnapshot(newQuote.getSnapshot());
			resp.setChangeReasons(List.of(ChangeReason.of("SECKILL_PRICE_CHANGED", null)));
			resp.setMessage("需要重新报价");
			idempotencyStorage.put(key, requestHash, resp);
			return resp;
		}
		if (!Objects.equals(currentShippingVersion, snapshot.getVersion().getShippingRulesVersion())) {
			CheckoutQuoteResponse newQuote = createQuote(payload.getQuoteRequest());
			resp.setStatus(CheckoutResultStatus.REQUOTE_REQUIRED);
			resp.setFinalQuoteId(newQuote.getQuoteId());
			resp.setSnapshot(newQuote.getSnapshot());
			resp.setChangeReasons(List.of(ChangeReason.of("SHIPPING_INPUT_CHANGED", null)));
			resp.setMessage("需要重新报价");
			idempotencyStorage.put(key, requestHash, resp);
			return resp;
		}

		List<ChangeReason> changes = new ArrayList<>();
		applyCouponUsageAndDegrade(payload, request.getTradeId(), now, changes);
		recomputeTotals(payload);

		entity.setSnapshot(writePayload(payload));
		entity.setUpdatedAt(now);
		checkoutQuoteRepository.save(entity);
		checkoutQuoteRepository.markCommitted(entity.getId(), request.getTradeId(), now);

		resp.setStatus(changes.isEmpty() ? CheckoutResultStatus.OK : CheckoutResultStatus.OK_WITH_CHANGE);
		resp.setFinalQuoteId(entity.getId().toString());
		resp.setSnapshot(payload.getSnapshot());
		resp.setChangeReasons(changes);
		resp.setMessage(changes.isEmpty() ? "成功" : "已降级/部分优惠失效");
		idempotencyStorage.put(key, requestHash, resp);
		return resp;
	}

	@Transactional
	public CheckoutReleaseResponse release(String idempotencyKey, CheckoutReleaseRequest request) {
		String key = "promotion:checkout:release:" + idempotencyKey;
		String requestHash = sha256(String.valueOf(request.getQuoteId()) + "|" + String.valueOf(request.getTradeId()) + "|" + String.valueOf(request.getReason()));
		IdempotencyStorage.StoredValue stored = idempotencyStorage.get(key);
		if (stored != null) {
			if (!Objects.equals(requestHash, stored.requestHash())) {
				return CheckoutReleaseResponse.of(false, "IDEMPOTENCY_CONFLICT");
			}
			Object v = stored.value();
			if (v instanceof CheckoutReleaseResponse r) {
				return r;
			}
		}
		LocalDateTime now = LocalDateTime.now();
		UUID quoteId;
		try {
			quoteId = UUID.fromString(request.getQuoteId());
		} catch (Exception e) {
			CheckoutReleaseResponse resp = CheckoutReleaseResponse.of(true, "released");
			idempotencyStorage.put(key, requestHash, resp);
			return resp;
		}

		Optional<CheckoutQuoteEntity> opt = checkoutQuoteRepository.findById(quoteId);
		if (opt.isPresent()) {
			CheckoutQuoteEntity entity = opt.get();
			CheckoutQuotePayload payload = readPayload(entity.getSnapshot());
			if ("QUOTED".equals(entity.getStatus())) {
				releaseLocks(payload, now);
				checkoutQuoteRepository.updateStatus(entity.getId(), "QUOTED", "RELEASED", now);
			}
		}
		CheckoutReleaseResponse resp = CheckoutReleaseResponse.of(true, "released");
		idempotencyStorage.put(key, requestHash, resp);
		return resp;
	}

	private CheckoutQuoteResponse createQuote(CheckoutQuoteRequest request) {
		Objects.requireNonNull(request, "request");
		LocalDateTime now = LocalDateTime.now();
		UUID quoteId = UUID.randomUUID();
		LocalDateTime expiresAt = now.plusSeconds(quoteTtlSeconds);
		String inputHash = computeInputHash(request);

		PricingSnapshot snapshot = new PricingSnapshot();
		List<PricingSnapshot.PricedLine> lines = priceLinesWithSeckill(request, now);
		snapshot.setLines(lines);
		long itemsTotal = lines.stream().mapToLong(PricingSnapshot.PricedLine::getLineSubtotalCents).sum();
		snapshot.setItemsTotalCents(itemsTotal);

		long promotionDiscountAbs = applyFullReduction(snapshot, now);
		long shippingFee = calculateShippingFee(request, itemsTotal);
		snapshot.setShippingFeeCents(shippingFee);

		CouponPlanResult couponPlan = selectAndLockCoupons(quoteId, request, snapshot, now);
		List<PricingSnapshot.AppliedBenefit> mergedBenefits = new ArrayList<>(snapshot.getAppliedBenefits());
		mergedBenefits.addAll(couponPlan.appliedBenefits);
		snapshot.setAppliedBenefits(mergedBenefits);

		long couponDiscountAbs = mergedBenefits.stream()
			.filter(b -> b.getAmountCents() < 0)
			.filter(b -> "PLATFORM_COUPON".equals(b.getBenefitType()) || "SHOP_COUPON".equals(b.getBenefitType()))
			.mapToLong(b -> Math.abs(b.getAmountCents()))
			.sum();
		snapshot.setCouponDiscountTotalCents(couponDiscountAbs);
		snapshot.setPromotionDiscountTotalCents(promotionDiscountAbs);
		allocateDiscounts(snapshot);
		long payable = snapshot.getItemsTotalCents() + snapshot.getShippingFeeCents()
			- snapshot.getPromotionDiscountTotalCents() - snapshot.getCouponDiscountTotalCents();
		if (payable < 0) {
			payable = 0;
		}
		snapshot.setPayableCents(payable);

		PricingSnapshot.SnapshotVersion v = new PricingSnapshot.SnapshotVersion();
		v.setInputHash(inputHash);
		v.setPricingRulesVersion(computePricingRulesVersion(now));
		v.setShippingRulesVersion(computeShippingRulesVersion());
		snapshot.setVersion(v);

		CheckoutQuotePayload payload = new CheckoutQuotePayload();
		payload.setQuoteRequest(request);
		payload.setSnapshot(snapshot);
		payload.setCouponGroupPlans(couponPlan.groupPlans);

		CheckoutQuoteEntity entity = new CheckoutQuoteEntity();
		entity.setId(quoteId);
		entity.setUserId(request.getUserId());
		entity.setStatus("QUOTED");
		entity.setInputHash(inputHash);
		entity.setPricingRulesVersion(v.getPricingRulesVersion());
		entity.setShippingRulesVersion(v.getShippingRulesVersion());
		entity.setSnapshot(writePayload(payload));
		entity.setExpiresAt(expiresAt);
		entity.setTradeId(null);
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now);
		checkoutQuoteRepository.save(entity);

		CheckoutQuoteResponse resp = new CheckoutQuoteResponse();
		resp.setStatus(CheckoutResultStatus.OK);
		resp.setQuoteId(quoteId.toString());
		resp.setExpiresAtEpochMs(expiresAt.toInstant(ZoneOffset.UTC).toEpochMilli());
		resp.setSnapshot(snapshot);
		resp.setChangeReasons(List.of());
		return resp;
	}

	private String computeInputHash(CheckoutQuoteRequest request) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(String.valueOf(request.getUserId()).getBytes(StandardCharsets.UTF_8));
			md.update((byte) '|');
			md.update(String.valueOf(request.getAddressId()).getBytes(StandardCharsets.UTF_8));
			md.update((byte) '|');
			List<CheckoutQuoteRequest.Line> lines = new ArrayList<>(request.getLines());
			lines.sort(Comparator.comparing(CheckoutQuoteRequest.Line::getShopId)
				.thenComparing(CheckoutQuoteRequest.Line::getSkuId));
			for (CheckoutQuoteRequest.Line l : lines) {
				md.update(String.valueOf(l.getShopId()).getBytes(StandardCharsets.UTF_8));
				md.update((byte) ':');
				md.update(String.valueOf(l.getSkuId()).getBytes(StandardCharsets.UTF_8));
				md.update((byte) ':');
				md.update(String.valueOf(l.getQuantity()).getBytes(StandardCharsets.UTF_8));
				md.update((byte) ':');
				md.update(String.valueOf(l.getBaseUnitPriceCents()).getBytes(StandardCharsets.UTF_8));
				md.update((byte) ':');
				md.update(String.valueOf(l.getWeightGrams()).getBytes(StandardCharsets.UTF_8));
				md.update((byte) '|');
			}
			byte[] digest = md.digest();
			StringBuilder sb = new StringBuilder(digest.length * 2);
			for (byte b : digest) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception e) {
			throw new IllegalStateException("failed to compute input hash", e);
		}
	}

	private List<PricingSnapshot.PricedLine> priceLinesWithSeckill(CheckoutQuoteRequest request, LocalDateTime now) {
		List<SeckillPriceRuleEntity> rules = seckillPriceRuleRepository.findActive(now);
		List<PricingSnapshot.PricedLine> pricedLines = new ArrayList<>();
		List<CheckoutQuoteRequest.Line> lines = new ArrayList<>(request.getLines());
		lines.sort(Comparator.comparing(CheckoutQuoteRequest.Line::getShopId)
			.thenComparing(CheckoutQuoteRequest.Line::getSkuId));

		for (CheckoutQuoteRequest.Line l : lines) {
			int qty = l.getQuantity() == null ? 0 : l.getQuantity();
			long baseUnit = l.getBaseUnitPriceCents() == null ? 0L : l.getBaseUnitPriceCents();
			long finalUnit = resolveSeckillFinalUnitPriceCents(rules, l.getShopId(), l.getSkuId(), baseUnit);
			long subtotal = Math.multiplyExact(finalUnit, qty);
			PricingSnapshot.PricedLine pl = new PricingSnapshot.PricedLine();
			pl.setSkuId(l.getSkuId());
			pl.setShopId(l.getShopId());
			pl.setQuantity(qty);
			pl.setBaseUnitPriceCents(baseUnit);
			pl.setFinalUnitPriceCents(finalUnit);
			pl.setLineSubtotalCents(subtotal);
			pl.setLineDiscountAllocatedCents(0L);
			pl.setLinePayableCents(subtotal);
			pricedLines.add(pl);
		}
		return pricedLines;
	}

	private long resolveSeckillFinalUnitPriceCents(List<SeckillPriceRuleEntity> rules, String shopId, String skuId,
		long baseUnitPriceCents) {
		for (SeckillPriceRuleEntity r : rules) {
			JsonNode scope = safeReadJson(r.getScope());
			if (!matchScope(scope, shopId, skuId)) {
				continue;
			}
			JsonNode content = safeReadJson(r.getContent());
			if (content == null) {
				continue;
			}
			JsonNode p = content.get("finalUnitPriceCents");
			if (p != null && p.isNumber()) {
				return p.asLong();
			}
		}
		return baseUnitPriceCents;
	}

	private boolean matchScope(JsonNode scope, String shopId, String skuId) {
		if (scope == null || scope.isNull()) {
			return true;
		}
		JsonNode skuIds = scope.get("skuIds");
		if (skuIds != null && skuIds.isArray()) {
			boolean ok = false;
			for (JsonNode n : skuIds) {
				if (Objects.equals(skuId, n.asText())) {
					ok = true;
					break;
				}
			}
			if (!ok) {
				return false;
			}
		}
		JsonNode shopIds = scope.get("shopIds");
		if (shopIds != null && shopIds.isArray()) {
			boolean ok = false;
			for (JsonNode n : shopIds) {
				if (Objects.equals(shopId, n.asText())) {
					ok = true;
					break;
				}
			}
			if (!ok) {
				return false;
			}
		}
		return true;
	}

	private long applyFullReduction(PricingSnapshot snapshot, LocalDateTime now) {
		List<FullReductionCampaignEntity> campaigns = fullReductionCampaignRepository.findActive(now);
		long itemsTotal = snapshot.getItemsTotalCents();
		long bestOff = 0L;
		String bestCampaignId = null;
		String bestTrace = null;
		for (FullReductionCampaignEntity c : campaigns) {
			JsonNode ladder = safeReadJson(c.getLadder());
			if (ladder == null || !ladder.isArray()) {
				continue;
			}
			for (JsonNode step : ladder) {
				JsonNode th = step.get("threshold");
				JsonNode off = step.get("off");
				if (th == null || off == null || !th.isNumber() || !off.isNumber()) {
					continue;
				}
				long threshold = th.asLong();
				long offCents = off.asLong();
				if (itemsTotal >= threshold && offCents > bestOff) {
					bestOff = offCents;
					bestCampaignId = c.getId().toString();
					bestTrace = "campaign:" + c.getName() + ":" + threshold + "->" + offCents;
				}
			}
		}
		if (bestOff > 0) {
			PricingSnapshot.AppliedBenefit b = new PricingSnapshot.AppliedBenefit();
			b.setBenefitType("PLATFORM_FULL_REDUCTION");
			b.setBenefitId(bestCampaignId);
			b.setGroupKey(null);
			b.setLockId(null);
			b.setAmountCents(-bestOff);
			b.setRuleTrace(bestTrace);
			List<PricingSnapshot.AppliedBenefit> benefits = new ArrayList<>(snapshot.getAppliedBenefits());
			benefits.add(b);
			snapshot.setAppliedBenefits(benefits);
		}
		return bestOff;
	}

	private long calculateShippingFee(CheckoutQuoteRequest request, long itemsTotalCents) {
		List<ShippingRuleEntity> rules = shippingRuleRepository.findByStatusOrderByVersionDesc("ACTIVE");
		if (rules.isEmpty()) {
			return 0L;
		}
		ShippingRuleEntity r = rules.get(0);
		JsonNode content = safeReadJson(r.getContent());
		if (content == null) {
			return 0L;
		}
		long baseFee = asLongOrZero(content.get("baseFeeCents"));
		long feePerGram = asLongOrZero(content.get("feePerGramCents"));
		long freeThreshold = asLongOrZero(content.get("freeThresholdCents"));
		long totalWeight = 0L;
		for (CheckoutQuoteRequest.Line l : request.getLines()) {
			int qty = l.getQuantity() == null ? 0 : l.getQuantity();
			long w = l.getWeightGrams() == null ? 0L : l.getWeightGrams();
			totalWeight += Math.multiplyExact(w, qty);
		}
		long fee = baseFee + Math.multiplyExact(totalWeight, feePerGram);
		if (freeThreshold > 0 && itemsTotalCents >= freeThreshold) {
			fee = 0L;
		}
		if (fee < 0) {
			fee = 0L;
		}
		return fee;
	}

	private CouponPlanResult selectAndLockCoupons(UUID quoteId, CheckoutQuoteRequest request, PricingSnapshot snapshot,
		LocalDateTime now) {
		List<String> platformIds = request.getAppliedIntent() == null ? List.of() : request.getAppliedIntent().getPlatformCouponIds();
		Map<String, List<String>> shopMap = request.getAppliedIntent() == null ? Map.of() : request.getAppliedIntent().getShopCouponIdsByShop();

		List<ScopedCouponId> inputs = new ArrayList<>();
		for (String s : platformIds) {
			UUID id = parseUuid(s);
			if (id != null) {
				inputs.add(new ScopedCouponId(id, null));
			}
		}
		for (Map.Entry<String, List<String>> e : shopMap.entrySet()) {
			String shopId = e.getKey();
			for (String s : e.getValue()) {
				UUID id = parseUuid(s);
				if (id != null) {
					inputs.add(new ScopedCouponId(id, shopId));
				}
			}
		}

		List<PricingSnapshot.AppliedBenefit> benefits = new ArrayList<>();
		List<CouponGroupPlan> plans = new ArrayList<>();

		Map<String, Long> shopSubtotals = new HashMap<>();
		for (PricingSnapshot.PricedLine l : snapshot.getLines()) {
			shopSubtotals.merge(l.getShopId(), l.getLineSubtotalCents(), Long::sum);
		}

		Map<String, List<CouponCandidate>> candidatesByGroup = new HashMap<>();
		for (ScopedCouponId input : inputs) {
			Optional<CouponEntity> cOpt = couponRepository.findById(input.couponId());
			if (cOpt.isEmpty()) {
				continue;
			}
			CouponEntity c = cOpt.get();
			if (!"ACTIVE".equals(c.getStatus())) {
				continue;
			}
			if (c.getStartTime().isAfter(now) || !c.getEndTime().isAfter(now)) {
				continue;
			}
			String mutexGroup = c.getMutexGroup() == null || c.getMutexGroup().isBlank() ? "DEFAULT" : c.getMutexGroup();
			String groupKey;
			long baseAmount;
			if (input.shopId() == null) {
				if (c.getShopId() != null && !c.getShopId().isBlank()) {
					continue;
				}
				groupKey = "PLATFORM:" + mutexGroup;
				baseAmount = snapshot.getItemsTotalCents();
			} else {
				if (c.getShopId() == null || !Objects.equals(c.getShopId(), input.shopId())) {
					continue;
				}
				groupKey = "SHOP:" + input.shopId() + ":" + mutexGroup;
				baseAmount = shopSubtotals.getOrDefault(input.shopId(), 0L);
			}
			long discount = estimateCouponDiscountCents(c, baseAmount);
			if (discount <= 0L) {
				continue;
			}
			candidatesByGroup.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(new CouponCandidate(c, discount));
		}

		for (Map.Entry<String, List<CouponCandidate>> entry : candidatesByGroup.entrySet()) {
			String group = entry.getKey();
			List<CouponCandidate> candidates = new ArrayList<>(entry.getValue());
			candidates.sort(Comparator.comparingLong(CouponCandidate::discountCents).reversed()
				.thenComparingInt(a -> a.coupon.getPriority()).reversed()
				.thenComparing(a -> a.coupon.getId().toString()));

			CouponGroupPlan plan = new CouponGroupPlan();
			plan.setGroupKey(group);
			plan.setCandidateCouponIds(candidates.stream().map(cand -> cand.coupon.getId().toString()).toList());
			plans.add(plan);

			for (CouponCandidate cand : candidates) {
				Optional<UserCouponEntity> userCouponOpt = userCouponRepository
					.findFirstByUserIdAndCouponIdAndUseStatusOrderByReceiveTimeAsc(request.getUserId(), cand.coupon.getId(),
						"UNUSED");
				if (userCouponOpt.isEmpty()) {
					continue;
				}
				UserCouponEntity uc = userCouponOpt.get();
				String lockId = UUID.randomUUID().toString();
				LocalDateTime expireAt = now.plusMinutes(couponLockTimeoutMinutes);
				int locked = userCouponRepository.lockUnused(uc.getId(), lockId, expireAt, now);
				if (locked <= 0) {
					continue;
				}
				PricingSnapshot.AppliedBenefit b = new PricingSnapshot.AppliedBenefit();
				b.setBenefitType(group.startsWith("SHOP:") ? "SHOP_COUPON" : "PLATFORM_COUPON");
				b.setBenefitId(cand.coupon.getId().toString());
				b.setGroupKey(group);
				b.setLockId(lockId);
				b.setAmountCents(-cand.discountCents);
				b.setRuleTrace("coupon:" + cand.coupon.getCouponNo());
				benefits.add(b);
				break;
			}
		}

		return new CouponPlanResult(benefits, plans);
	}

	private long estimateCouponDiscountCents(CouponEntity coupon, long baseAmountCents) {
		long threshold = toCents(coupon.getThresholdAmount());
		if (threshold > 0 && baseAmountCents < threshold) {
			return 0L;
		}
		long fixed = toCents(coupon.getDiscountAmount());
		long max = toCents(coupon.getMaxDiscountAmount());
		BigDecimal rate = coupon.getDiscountRate();
		long discount;
		if (fixed > 0) {
			discount = fixed;
		} else if (rate != null && rate.compareTo(BigDecimal.ZERO) > 0) {
			BigDecimal base = BigDecimal.valueOf(baseAmountCents);
			BigDecimal finalAmount = base.multiply(rate).setScale(0, RoundingMode.DOWN);
			discount = base.subtract(finalAmount).longValue();
		} else {
			return 0L;
		}
		if (discount < 0) {
			discount = 0;
		}
		if (max > 0 && discount > max) {
			discount = max;
		}
		if (discount > baseAmountCents) {
			discount = baseAmountCents;
		}
		return discount;
	}

	private void allocateDiscounts(PricingSnapshot snapshot) {
		List<PricingSnapshot.PricedLine> lines = snapshot.getLines();
		if (lines.isEmpty()) {
			return;
		}
		for (PricingSnapshot.PricedLine l : lines) {
			l.setLineDiscountAllocatedCents(0L);
			l.setLinePayableCents(l.getLineSubtotalCents());
		}

		long orderLevelDiscount = 0L;
		for (PricingSnapshot.AppliedBenefit b : snapshot.getAppliedBenefits()) {
			if (b.getAmountCents() >= 0) {
				continue;
			}
			if ("PLATFORM_COUPON".equals(b.getBenefitType()) || "PLATFORM_FULL_REDUCTION".equals(b.getBenefitType())) {
				orderLevelDiscount += Math.abs(b.getAmountCents());
			}
		}
		allocateToLines(lines, orderLevelDiscount, null);

		Map<String, Long> shopDiscount = new HashMap<>();
		for (PricingSnapshot.AppliedBenefit b : snapshot.getAppliedBenefits()) {
			if (b.getAmountCents() >= 0) {
				continue;
			}
			if (!"SHOP_COUPON".equals(b.getBenefitType())) {
				continue;
			}
			String group = b.getGroupKey();
			if (group != null && group.startsWith("SHOP:")) {
				String shopId = parseShopIdFromGroupKey(group);
				if (shopId == null) {
					continue;
				}
				shopDiscount.merge(shopId, Math.abs(b.getAmountCents()), Long::sum);
			}
		}
		for (Map.Entry<String, Long> e : shopDiscount.entrySet()) {
			allocateToLines(lines, e.getValue(), e.getKey());
		}

		long allocatedTotal = lines.stream().mapToLong(PricingSnapshot.PricedLine::getLineDiscountAllocatedCents).sum();
		long totalDiscount = snapshot.getPromotionDiscountTotalCents() + snapshot.getCouponDiscountTotalCents();
		if (allocatedTotal != totalDiscount) {
			snapshot.setCouponDiscountTotalCents(Math.max(0L, allocatedTotal - snapshot.getPromotionDiscountTotalCents()));
		}
	}

	private void allocateToLines(List<PricingSnapshot.PricedLine> lines, long discountAbs, String onlyShopId) {
		if (discountAbs <= 0) {
			return;
		}
		List<Integer> idx = new ArrayList<>();
		long base = 0L;
		for (int i = 0; i < lines.size(); i++) {
			PricingSnapshot.PricedLine l = lines.get(i);
			if (onlyShopId != null && !Objects.equals(onlyShopId, l.getShopId())) {
				continue;
			}
			idx.add(i);
			base += l.getLineSubtotalCents();
		}
		if (idx.isEmpty() || base <= 0) {
			return;
		}
		long allocated = 0L;
		for (int k = 0; k < idx.size(); k++) {
			int i = idx.get(k);
			PricingSnapshot.PricedLine l = lines.get(i);
			long raw = l.getLineSubtotalCents();
			long alloc;
			if (k < idx.size() - 1) {
				alloc = (long) Math.floor((double) discountAbs * raw / base);
			} else {
				alloc = discountAbs - allocated;
			}
			long maxAlloc = raw - l.getLineDiscountAllocatedCents();
			if (alloc > maxAlloc) {
				alloc = maxAlloc;
			}
			if (alloc < 0) {
				alloc = 0;
			}
			l.setLineDiscountAllocatedCents(l.getLineDiscountAllocatedCents() + alloc);
			l.setLinePayableCents(raw - l.getLineDiscountAllocatedCents());
			allocated += alloc;
		}
	}

	private void applyCouponUsageAndDegrade(CheckoutQuotePayload payload, String tradeId, LocalDateTime now,
		List<ChangeReason> changes) {
		PricingSnapshot snapshot = payload.getSnapshot();
		List<PricingSnapshot.AppliedBenefit> benefits = new ArrayList<>(snapshot.getAppliedBenefits());
		Map<String, CouponGroupPlan> planByGroup = new HashMap<>();
		for (CouponGroupPlan p : payload.getCouponGroupPlans()) {
			planByGroup.put(p.getGroupKey(), p);
		}
		for (int i = 0; i < benefits.size(); i++) {
			PricingSnapshot.AppliedBenefit b = benefits.get(i);
			if (!"PLATFORM_COUPON".equals(b.getBenefitType()) && !"SHOP_COUPON".equals(b.getBenefitType())) {
				continue;
			}
			String lockId = b.getLockId();
			if (lockId == null) {
				continue;
			}
			int used = userCouponRepository.useByLockId(lockId, tradeId, now, now);
			if (used > 0) {
				continue;
			}
			CouponGroupPlan plan = planByGroup.get(b.getGroupKey());
			boolean replaced = false;
			if (plan != null) {
				for (String candCouponIdStr : plan.getCandidateCouponIds()) {
					if (Objects.equals(candCouponIdStr, b.getBenefitId())) {
						continue;
					}
					UUID candCouponId = parseUuid(candCouponIdStr);
					if (candCouponId == null) {
						continue;
					}
					Optional<UserCouponEntity> userCouponOpt = userCouponRepository
						.findFirstByUserIdAndCouponIdAndUseStatusOrderByReceiveTimeAsc(payload.getQuoteRequest().getUserId(),
							candCouponId, "UNUSED");
					if (userCouponOpt.isEmpty()) {
						continue;
					}
					UserCouponEntity uc = userCouponOpt.get();
					String newLockId = UUID.randomUUID().toString();
					LocalDateTime expireAt = now.plusMinutes(couponLockTimeoutMinutes);
					int locked = userCouponRepository.lockUnused(uc.getId(), newLockId, expireAt, now);
					if (locked <= 0) {
						continue;
					}
					int used2 = userCouponRepository.useByLockId(newLockId, tradeId, now, now);
					if (used2 <= 0) {
						userCouponRepository.unlockByLockId(newLockId, now);
						continue;
					}
					Optional<CouponEntity> cOpt = couponRepository.findById(candCouponId);
					if (cOpt.isEmpty()) {
						continue;
					}
					CouponEntity c = cOpt.get();
					String shopId = parseShopIdFromGroupKey(b.getGroupKey());
					long baseAmount = shopId == null
						? snapshot.getItemsTotalCents()
						: snapshot.getLines().stream().filter(l -> Objects.equals(l.getShopId(), shopId))
							.mapToLong(PricingSnapshot.PricedLine::getLineSubtotalCents).sum();
					long discount = estimateCouponDiscountCents(c, baseAmount);
					if (discount <= 0) {
						replaced = false;
						break;
					}
					String oldCouponId = b.getBenefitId();
					b.setBenefitId(candCouponIdStr);
					b.setLockId(newLockId);
					b.setAmountCents(-discount);
					b.setRuleTrace("coupon:" + c.getCouponNo());
					changes.add(ChangeReason.of("COUPON_DEGRADED", oldCouponId + "->" + candCouponIdStr));
					replaced = true;
					break;
				}
			}
			if (!replaced) {
				changes.add(ChangeReason.of("COUPON_INVALIDATED", b.getBenefitId()));
				benefits.remove(i);
				i--;
			}
		}
		snapshot.setAppliedBenefits(benefits);
	}

	private void recomputeTotals(CheckoutQuotePayload payload) {
		PricingSnapshot snapshot = payload.getSnapshot();
		long promotionDiscountAbs = 0L;
		long couponDiscountAbs = 0L;
		for (PricingSnapshot.AppliedBenefit b : snapshot.getAppliedBenefits()) {
			if (b.getAmountCents() >= 0) {
				continue;
			}
			if ("PLATFORM_FULL_REDUCTION".equals(b.getBenefitType())) {
				promotionDiscountAbs += Math.abs(b.getAmountCents());
			} else if ("PLATFORM_COUPON".equals(b.getBenefitType()) || "SHOP_COUPON".equals(b.getBenefitType())) {
				couponDiscountAbs += Math.abs(b.getAmountCents());
			}
		}
		snapshot.setPromotionDiscountTotalCents(promotionDiscountAbs);
		snapshot.setCouponDiscountTotalCents(couponDiscountAbs);
		allocateDiscounts(snapshot);
		long payable = snapshot.getItemsTotalCents() + snapshot.getShippingFeeCents()
			- snapshot.getPromotionDiscountTotalCents() - snapshot.getCouponDiscountTotalCents();
		if (payable < 0) {
			payable = 0;
		}
		snapshot.setPayableCents(payable);
	}

	private void releaseLocks(CheckoutQuotePayload payload, LocalDateTime now) {
		for (PricingSnapshot.AppliedBenefit b : payload.getSnapshot().getAppliedBenefits()) {
			if (b.getLockId() != null) {
				userCouponRepository.unlockByLockId(b.getLockId(), now);
			}
		}
	}

	private String computePricingRulesVersion(LocalDateTime now) {
		long seckillV = seckillPriceRuleRepository.findActive(now).stream().mapToLong(SeckillPriceRuleEntity::getVersion).max().orElse(0L);
		long fullReductionV = fullReductionCampaignRepository.findActive(now).stream().map(FullReductionCampaignEntity::getUpdatedAt)
			.filter(Objects::nonNull)
			.mapToLong(t -> t.toInstant(ZoneOffset.UTC).toEpochMilli())
			.max().orElse(0L);
		String couponPolicyV = "mutexGroup-v1";
		return "seckill:" + seckillV + "|fullReduction:" + fullReductionV + "|couponPolicy:" + couponPolicyV;
	}

	private String computeShippingRulesVersion() {
		List<ShippingRuleEntity> rules = shippingRuleRepository.findByStatusOrderByVersionDesc("ACTIVE");
		long shippingV = rules.isEmpty() ? 0L : rules.get(0).getVersion();
		return "shipping:" + shippingV;
	}

	private String sha256(String s) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(digest.length * 2);
			for (byte b : digest) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception e) {
			throw new IllegalStateException("failed to compute hash", e);
		}
	}

	private CheckoutQuotePayload readPayload(String json) {
		try {
			return objectMapper.readValue(json, CheckoutQuotePayload.class);
		} catch (Exception e) {
			throw new IllegalStateException("failed to read quote payload", e);
		}
	}

	private String writePayload(CheckoutQuotePayload payload) {
		try {
			return objectMapper.writeValueAsString(payload);
		} catch (Exception e) {
			throw new IllegalStateException("failed to write quote payload", e);
		}
	}

	private JsonNode safeReadJson(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			return objectMapper.readTree(json);
		} catch (Exception e) {
			return null;
		}
	}

	private long asLongOrZero(JsonNode node) {
		if (node == null || !node.isNumber()) {
			return 0L;
		}
		return node.asLong();
	}

	private long toCents(BigDecimal amount) {
		if (amount == null) {
			return 0L;
		}
		return amount.movePointRight(2).setScale(0, RoundingMode.DOWN).longValue();
	}

	private UUID parseUuid(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(s);
		} catch (Exception e) {
			return null;
		}
	}

	private String parseShopIdFromGroupKey(String groupKey) {
		if (groupKey == null) {
			return null;
		}
		if (!groupKey.startsWith("SHOP:")) {
			return null;
		}
		String[] parts = groupKey.split(":", 3);
		if (parts.length < 2) {
			return null;
		}
		return parts[1];
	}

	private record ScopedCouponId(UUID couponId, String shopId) {
	}

	private static class CouponCandidate {
		private final CouponEntity coupon;
		private final long discountCents;

		private CouponCandidate(CouponEntity coupon, long discountCents) {
			this.coupon = coupon;
			this.discountCents = discountCents;
		}

		private long discountCents() {
			return discountCents;
		}
	}

	private record CouponPlanResult(List<PricingSnapshot.AppliedBenefit> appliedBenefits, List<CouponGroupPlan> groupPlans) {
	}

	public static class CouponGroupPlan {
		private String groupKey;
		private List<String> candidateCouponIds = List.of();

		public String getGroupKey() {
			return groupKey;
		}

		public void setGroupKey(String groupKey) {
			this.groupKey = groupKey;
		}

		public List<String> getCandidateCouponIds() {
			return candidateCouponIds;
		}

		public void setCandidateCouponIds(List<String> candidateCouponIds) {
			this.candidateCouponIds = candidateCouponIds == null ? List.of() : candidateCouponIds;
		}
	}

	public static class CheckoutQuotePayload {
		private CheckoutQuoteRequest quoteRequest;
		private PricingSnapshot snapshot;
		private List<CouponGroupPlan> couponGroupPlans = List.of();

		public CheckoutQuoteRequest getQuoteRequest() {
			return quoteRequest;
		}

		public void setQuoteRequest(CheckoutQuoteRequest quoteRequest) {
			this.quoteRequest = quoteRequest;
		}

		public PricingSnapshot getSnapshot() {
			return snapshot;
		}

		public void setSnapshot(PricingSnapshot snapshot) {
			this.snapshot = snapshot;
		}

		public List<CouponGroupPlan> getCouponGroupPlans() {
			return couponGroupPlans;
		}

		public void setCouponGroupPlans(List<CouponGroupPlan> couponGroupPlans) {
			this.couponGroupPlans = couponGroupPlans == null ? List.of() : couponGroupPlans;
		}
	}
}
