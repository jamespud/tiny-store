package com.github.spud.tinystore.promotion.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;

import com.github.spud.tinystore.promotion.application.service.CheckoutAppService.CheckoutQuotePayload;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.CheckoutQuoteEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaCheckoutQuoteRepository;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutCommitRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutCommitResponse;
import com.github.spud.tinystore.promotion.interfaces.dto.ConfirmUseRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.ConfirmUseResponse;
import com.github.spud.tinystore.promotion.interfaces.dto.PreUseResponse;
import com.github.spud.tinystore.promotion.interfaces.dto.PricingSnapshot;

@Service
public class ConfirmUseAppService {

	private final CheckoutAppService checkoutAppService;
	private final JpaCheckoutQuoteRepository checkoutQuoteRepository;
	private final ObjectMapper objectMapper;

	public ConfirmUseAppService(CheckoutAppService checkoutAppService, JpaCheckoutQuoteRepository checkoutQuoteRepository,
		ObjectMapper objectMapper) {
		this.checkoutAppService = checkoutAppService;
		this.checkoutQuoteRepository = checkoutQuoteRepository;
		this.objectMapper = objectMapper;
	}

	public ConfirmUseResponse confirm(String idempotencyKey, ConfirmUseRequest request) {
		ConfirmUseResponse resp = new ConfirmUseResponse();
		String inputHash = loadInputHash(request.getLockId());
		if (inputHash == null) {
			resp.setSuccess(false);
			resp.setMessage("REQUOTE_REQUIRED");
			return resp;
		}
		CheckoutCommitRequest commitReq = new CheckoutCommitRequest();
		commitReq.setQuoteId(request.getLockId());
		commitReq.setOrderNo(request.getOrderNo());
		commitReq.setPayNo(request.getPayNo());
		commitReq.setPaidAt(request.getPaidAt());
		commitReq.setInputHash(inputHash);

		CheckoutCommitResponse commitResp = checkoutAppService.commit(idempotencyKey, commitReq);
		boolean ok = commitResp.getStatus() != null && commitResp.getStatus().name().startsWith("OK");
		resp.setSuccess(ok);
		PricingSnapshot snapshot = commitResp.getSnapshot();
		if (snapshot != null) {
			resp.setAppliedCoupons(toAppliedCoupons(snapshot));
			resp.setTotalDiscount(toAmount(snapshot.getPromotionDiscountTotalCents() + snapshot.getCouponDiscountTotalCents()));
		}
		resp.setMessage(commitResp.getMessage());
		return resp;
	}

	private String loadInputHash(String quoteId) {
		UUID id;
		try {
			id = UUID.fromString(quoteId);
		} catch (Exception e) {
			return null;
		}
		Optional<CheckoutQuoteEntity> opt = checkoutQuoteRepository.findById(id);
		if (opt.isEmpty()) {
			return null;
		}
		CheckoutQuotePayload payload = readPayload(opt.get().getSnapshot());
		if (payload == null || payload.getSnapshot() == null || payload.getSnapshot().getVersion() == null) {
			return null;
		}
		return payload.getSnapshot().getVersion().getInputHash();
	}

	private CheckoutQuotePayload readPayload(String json) {
		try {
			return objectMapper.readValue(json, CheckoutQuotePayload.class);
		} catch (Exception e) {
			return null;
		}
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

	private BigDecimal toAmount(long centsAbs) {
		return BigDecimal.valueOf(centsAbs).movePointLeft(2).setScale(2, RoundingMode.DOWN);
	}
}
