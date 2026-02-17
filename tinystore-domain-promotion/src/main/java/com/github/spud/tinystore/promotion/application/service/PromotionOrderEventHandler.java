package com.github.spud.tinystore.promotion.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.CheckoutQuoteEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.ConsumerEventLogEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaCheckoutQuoteRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaConsumerEventLogRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaUserCouponRepository;
import com.github.spud.tinystore.promotion.interfaces.dto.PricingSnapshot;

/**
 * 促销订单事件处理器
 * 处理订单域事件，实现优惠券状态流转
 */
@Service
public class PromotionOrderEventHandler {

	private static final Logger log = LoggerFactory.getLogger(PromotionOrderEventHandler.class);
	private static final String CONSUMER_NAME = "promotion-order-consumer";

	private final JpaCheckoutQuoteRepository checkoutQuoteRepository;
	private final JpaUserCouponRepository userCouponRepository;
	private final JpaConsumerEventLogRepository consumerEventLogRepository;
	private final ObjectMapper objectMapper;

	public PromotionOrderEventHandler(JpaCheckoutQuoteRepository checkoutQuoteRepository,
		JpaUserCouponRepository userCouponRepository,
		JpaConsumerEventLogRepository consumerEventLogRepository,
		ObjectMapper objectMapper) {
		this.checkoutQuoteRepository = checkoutQuoteRepository;
		this.userCouponRepository = userCouponRepository;
		this.consumerEventLogRepository = consumerEventLogRepository;
		this.objectMapper = objectMapper;
	}

	/**
	 * 处理交易已支付事件
	 * 将 LOCKED 优惠券转为 USED
	 *
	 * @param eventId 事件 ID
	 * @param tradeId 交易 ID
	 * @param payloadJson 事件载荷
	 */
	@Transactional
	public void handleTradePaid(String eventId, String tradeId, String payloadJson) {
		// 幂等检查
		if (!tryMarkProcessed(eventId)) {
			log.info("Event already processed, skip. eventId={}, tradeId={}", eventId, tradeId);
			return;
		}

		// 查询 checkout quote
		Optional<CheckoutQuoteEntity> quoteOpt = checkoutQuoteRepository.findByTradeId(tradeId);
		if (quoteOpt.isEmpty()) {
			log.warn("Checkout quote not found for tradeId={}, eventId={}. Skip coupon usage.", tradeId, eventId);
			return;
		}

		CheckoutQuoteEntity quoteEntity = quoteOpt.get();
		CheckoutAppService.CheckoutQuotePayload payload = parsePayload(quoteEntity.getSnapshot());
		if (payload == null || payload.getSnapshot() == null) {
			log.error("Failed to parse checkout quote snapshot. quoteId={}, tradeId={}", quoteEntity.getId(), tradeId);
			return;
		}

		// 提取 appliedBenefits 中的 lockId，将优惠券标记为已使用
		PricingSnapshot snapshot = payload.getSnapshot();
		List<PricingSnapshot.AppliedBenefit> benefits = snapshot.getAppliedBenefits();
		if (benefits == null || benefits.isEmpty()) {
			log.info("No applied benefits in quote. quoteId={}, tradeId={}", quoteEntity.getId(), tradeId);
			return;
		}

		LocalDateTime now = LocalDateTime.now();
		for (PricingSnapshot.AppliedBenefit benefit : benefits) {
			if (!"PLATFORM_COUPON".equals(benefit.getBenefitType()) && !"SHOP_COUPON".equals(benefit.getBenefitType())) {
				continue;
			}

			String lockId = benefit.getLockId();
			if (lockId == null || lockId.isBlank()) {
				log.warn("Benefit has no lockId. benefitType={}, benefitId={}", benefit.getBenefitType(),
					benefit.getBenefitId());
				continue;
			}

			// 调用 Repository 将 LOCKED → USED
			int updated = userCouponRepository.useByLockId(lockId, tradeId, now, now);
			if (updated > 0) {
				log.info("Coupon marked as USED. lockId={}, tradeId={}", lockId, tradeId);
			} else {
				log.warn("Failed to mark coupon as USED (already used or not locked?). lockId={}, tradeId={}", lockId,
					tradeId);
			}
		}

		log.info("TRADE_PAID event processed successfully. eventId={}, tradeId={}", eventId, tradeId);
	}

	/**
	 * 处理交易关闭事件
	 * 将 LOCKED 优惠券释放为 UNUSED
	 *
	 * @param eventId 事件 ID
	 * @param tradeId 交易 ID
	 * @param payloadJson 事件载荷
	 */
	@Transactional
	public void handleTradeClosed(String eventId, String tradeId, String payloadJson) {
		// 幂等检查
		if (!tryMarkProcessed(eventId)) {
			log.info("Event already processed, skip. eventId={}, tradeId={}", eventId, tradeId);
			return;
		}

		// 查询 checkout quote
		Optional<CheckoutQuoteEntity> quoteOpt = checkoutQuoteRepository.findByTradeId(tradeId);
		if (quoteOpt.isEmpty()) {
			log.warn("Checkout quote not found for tradeId={}, eventId={}. Skip coupon release.", tradeId, eventId);
			return;
		}

		CheckoutQuoteEntity quoteEntity = quoteOpt.get();
		CheckoutAppService.CheckoutQuotePayload payload = parsePayload(quoteEntity.getSnapshot());
		if (payload == null || payload.getSnapshot() == null) {
			log.error("Failed to parse checkout quote snapshot. quoteId={}, tradeId={}", quoteEntity.getId(), tradeId);
			return;
		}

		// 提取 appliedBenefits 中的 lockId，释放优惠券
		PricingSnapshot snapshot = payload.getSnapshot();
		List<PricingSnapshot.AppliedBenefit> benefits = snapshot.getAppliedBenefits();
		if (benefits == null || benefits.isEmpty()) {
			log.info("No applied benefits in quote. quoteId={}, tradeId={}", quoteEntity.getId(), tradeId);
			return;
		}

		LocalDateTime now = LocalDateTime.now();
		for (PricingSnapshot.AppliedBenefit benefit : benefits) {
			if (!"PLATFORM_COUPON".equals(benefit.getBenefitType()) && !"SHOP_COUPON".equals(benefit.getBenefitType())) {
				continue;
			}

			String lockId = benefit.getLockId();
			if (lockId == null || lockId.isBlank()) {
				log.warn("Benefit has no lockId. benefitType={}, benefitId={}", benefit.getBenefitType(),
					benefit.getBenefitId());
				continue;
			}

			// 调用 Repository 将 LOCKED → UNUSED
			int updated = userCouponRepository.unlockByLockId(lockId, now);
			if (updated > 0) {
				log.info("Coupon unlocked. lockId={}, tradeId={}", lockId, tradeId);
			} else {
				log.warn("Failed to unlock coupon (already released or not locked?). lockId={}, tradeId={}", lockId,
					tradeId);
			}
		}

		log.info("TRADE_CLOSED event processed successfully. eventId={}, tradeId={}", eventId, tradeId);
	}

	/**
	 * 幂等性控制：尝试标记事件为已处理
	 *
	 * @param eventId 事件 ID
	 * @return true 表示首次处理，false 表示已处理过
	 */
	private boolean tryMarkProcessed(String eventId) {
		// 先检查是否已处理
		if (consumerEventLogRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME)) {
			return false;
		}

		// 插入记录（依赖唯一索引保证原子性）
		try {
			ConsumerEventLogEntity logEntity = new ConsumerEventLogEntity(
				UUID.randomUUID(),
				eventId,
				CONSUMER_NAME,
				"PROCESSED",
				LocalDateTime.now(),
				null,
				LocalDateTime.now()
			);
			consumerEventLogRepository.save(logEntity);
			return true;
		} catch (Exception e) {
			// 唯一索引冲突，说明已被其他事务处理
			log.debug("Event already processed by another transaction. eventId={}", eventId);
			return false;
		}
	}

	/**
	 * 解析 checkout quote snapshot
	 */
	private CheckoutAppService.CheckoutQuotePayload parsePayload(String snapshotJson) {
		if (snapshotJson == null || snapshotJson.isBlank()) {
			return null;
		}
		try {
			return objectMapper.readValue(snapshotJson, CheckoutAppService.CheckoutQuotePayload.class);
		} catch (Exception e) {
			log.error("Failed to parse checkout quote payload", e);
			return null;
		}
	}
}
