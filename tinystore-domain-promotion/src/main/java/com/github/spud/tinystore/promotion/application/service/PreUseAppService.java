package com.github.spud.tinystore.promotion.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutQuoteRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutQuoteResponse;
import com.github.spud.tinystore.promotion.interfaces.dto.PreUseRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.PreUseResponse;
import com.github.spud.tinystore.promotion.interfaces.dto.PricingSnapshot;

@Service
public class PreUseAppService {

	private final CheckoutAppService checkoutAppService;

	public PreUseAppService(CheckoutAppService checkoutAppService) {
		this.checkoutAppService = checkoutAppService;
	}

	public PreUseResponse preUse(String idempotencyKey, PreUseRequest request) {
		CheckoutQuoteRequest quoteReq = new CheckoutQuoteRequest();
		quoteReq.setUserId(request.getUserId());
		quoteReq.setTraceId(request.getTraceId());
		quoteReq.setAddressId("DEFAULT");
		quoteReq.setLines(buildLines(request.getSkus()));
		if (request.getCouponIds() != null && !request.getCouponIds().isEmpty()) {
			CheckoutQuoteRequest.AppliedIntent intent = new CheckoutQuoteRequest.AppliedIntent();
			intent.setPlatformCouponIds(request.getCouponIds());
			intent.setShopCouponIdsByShop(Collections.emptyMap());
			quoteReq.setAppliedIntent(intent);
		}

		CheckoutQuoteResponse quoteResp = checkoutAppService.quote(idempotencyKey, quoteReq);
		PreUseResponse resp = new PreUseResponse();
		boolean ok = quoteResp.getStatus() != null && quoteResp.getStatus().name().startsWith("OK");
		resp.setValid(ok);
		resp.setLockId(quoteResp.getQuoteId());
		PricingSnapshot snapshot = quoteResp.getSnapshot();
		if (snapshot != null) {
			resp.setAppliedCoupons(toAppliedCoupons(snapshot));
			resp.setTotalDiscount(toAmount(snapshot.getPromotionDiscountTotalCents() + snapshot.getCouponDiscountTotalCents()));
		}
		if (!ok) {
			resp.setInvalidReason("REQUOTE_REQUIRED");
		}
		return resp;
	}

	private List<CheckoutQuoteRequest.Line> buildLines(List<PreUseRequest.SkuDetail> skus) {
		List<CheckoutQuoteRequest.Line> lines = new ArrayList<>();
		if (skus == null) {
			return lines;
		}
		for (PreUseRequest.SkuDetail s : skus) {
			CheckoutQuoteRequest.Line l = new CheckoutQuoteRequest.Line();
			l.setSkuId(s.getSkuId());
			l.setShopId("DEFAULT");
			l.setQuantity(s.getQuantity());
			l.setBaseUnitPriceCents(toCents(s.getPrice()));
			l.setWeightGrams(0L);
			lines.add(l);
		}
		return lines;
	}

	private List<PreUseResponse.AppliedCoupon> toAppliedCoupons(PricingSnapshot snapshot) {
		if (snapshot.getAppliedBenefits() == null || snapshot.getAppliedBenefits().isEmpty()) {
			return List.of();
		}
		List<PreUseResponse.AppliedCoupon> r = new ArrayList<>();
		for (PricingSnapshot.AppliedBenefit b : snapshot.getAppliedBenefits()) {
			if (b.getAmountCents() >= 0) {
				continue;
			}
			if (!"PLATFORM_COUPON".equals(b.getBenefitType()) && !"SHOP_COUPON".equals(b.getBenefitType())) {
				continue;
			}
			PreUseResponse.AppliedCoupon c = new PreUseResponse.AppliedCoupon();
			c.setCouponId(b.getBenefitId());
			c.setDiscountAmount(toAmount(Math.abs(b.getAmountCents())));
			c.setRuleTrace(b.getRuleTrace());
			r.add(c);
		}
		return r;
	}

	private long toCents(BigDecimal amount) {
		if (amount == null) {
			return 0L;
		}
		return amount.movePointRight(2).setScale(0, RoundingMode.DOWN).longValue();
	}

	private BigDecimal toAmount(long centsAbs) {
		return BigDecimal.valueOf(centsAbs).movePointLeft(2).setScale(2, RoundingMode.DOWN);
	}
}
