package com.github.spud.tinystore.promotion.infrastructure.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.MDC;
import com.github.spud.tinystore.interfaces.aspect.LogConstant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.promotion.application.service.CheckoutAppService.CheckoutQuotePayload;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.CheckoutQuoteEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaCheckoutQuoteRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaUserCouponRepository;
import com.github.spud.tinystore.promotion.interfaces.dto.PricingSnapshot;

@Component
public class QuoteExpiryTask {

	private final JpaCheckoutQuoteRepository checkoutQuoteRepository;
	private final JpaUserCouponRepository userCouponRepository;
	private final ObjectMapper objectMapper;

	public QuoteExpiryTask(JpaCheckoutQuoteRepository checkoutQuoteRepository, JpaUserCouponRepository userCouponRepository,
		ObjectMapper objectMapper) {
		this.checkoutQuoteRepository = checkoutQuoteRepository;
		this.userCouponRepository = userCouponRepository;
		this.objectMapper = objectMapper;
	}

	@Transactional
	@Scheduled(fixedDelayString = "PT5M")
	public void expireQuotes() {
		String traceId = UUID.randomUUID().toString();
		MDC.put("traceId", traceId);
		MDC.put(LogConstant.MDC_LOG_ID, traceId);
		try {
			LocalDateTime now = LocalDateTime.now();
			List<CheckoutQuoteEntity> expired = checkoutQuoteRepository.findExpiredQuoted(now);
			for (CheckoutQuoteEntity q : expired) {
				CheckoutQuotePayload payload = readPayload(q.getSnapshot());
				PricingSnapshot snapshot = payload.getSnapshot();
				if (snapshot != null && snapshot.getAppliedBenefits() != null) {
					for (PricingSnapshot.AppliedBenefit b : snapshot.getAppliedBenefits()) {
						if (b.getLockId() != null) {
							userCouponRepository.unlockByLockId(b.getLockId(), now);
						}
					}
				}
				checkoutQuoteRepository.updateStatus(q.getId(), "QUOTED", "EXPIRED", now);
			}
		} finally {
			MDC.clear();
		}
	}

	private CheckoutQuotePayload readPayload(String json) {
		try {
			return objectMapper.readValue(json, CheckoutQuotePayload.class);
		} catch (Exception e) {
			throw new IllegalStateException("failed to read quote payload", e);
		}
	}
}
