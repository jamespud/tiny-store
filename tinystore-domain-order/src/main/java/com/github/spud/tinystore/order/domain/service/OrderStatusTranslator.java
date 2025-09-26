package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.domain.event.OrderStatus;
import com.github.spud.tinystore.order.domain.status.CoreFlowStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 订单状态翻译器
 * <p>
 * 提供 CoreFlowStatus 和遗留 OrderStatus 之间的双向映射，
 * 确保在状态系统迁移过程中的向后兼容性。
 * <p>
 * 主要功能：
 * 1. 新状态到遗留状态的映射 (toLegacy)
 * 2. 遗留状态到新状态的映射 (toCore)
 * 3. 可选映射处理未知状态 (toCoreOptional)
 *
 * @author Spud
 * @date 2025/9/22
 */
@Component
public class OrderStatusTranslator {

	// Mapping from legacy OrderStatus to CoreFlowStatus
	private static final Map<OrderStatus, CoreFlowStatus> LEGACY_TO_CORE_MAP = Map.ofEntries(
		// Payment states
		Map.entry(OrderStatus.UNPAID, CoreFlowStatus.PENDING_PAYMENT),
		Map.entry(OrderStatus.PAID, CoreFlowStatus.PAID_CONFIRMED),

		// Fulfillment states
		Map.entry(OrderStatus.FULFILLMENT_PENDING, CoreFlowStatus.AWAITING_FULFILLMENT),
		Map.entry(OrderStatus.FULFILLING, CoreFlowStatus.FULFILLING),

		// Terminal states
		Map.entry(OrderStatus.COMPLETED, CoreFlowStatus.COMPLETED),
		Map.entry(OrderStatus.CANCELLED, CoreFlowStatus.CANCELLED)
	);

	// Reverse mapping from CoreFlowStatus to a representative OrderStatus
	private static final Map<CoreFlowStatus, OrderStatus> CORE_TO_LEGACY = Map.ofEntries(
		Map.entry(CoreFlowStatus.PENDING_PAYMENT, OrderStatus.UNPAID),
		Map.entry(CoreFlowStatus.PENDING_FINAL_PAYMENT, OrderStatus.UNPAID),
		Map.entry(CoreFlowStatus.PAID_CONFIRMED, OrderStatus.PAID),
		Map.entry(CoreFlowStatus.AWAITING_FULFILLMENT, OrderStatus.FULFILLMENT_PENDING),
		Map.entry(CoreFlowStatus.FULFILLING, OrderStatus.FULFILLING),
		Map.entry(CoreFlowStatus.AFTER_SALE, OrderStatus.AFTER_SALE),
		Map.entry(CoreFlowStatus.CANCELLING, OrderStatus.CANCELLING),
		Map.entry(CoreFlowStatus.COMPLETED, OrderStatus.COMPLETED),
		Map.entry(CoreFlowStatus.CANCELLED, OrderStatus.CANCELLED),
		Map.entry(CoreFlowStatus.CLOSED, OrderStatus.CLOSED),
		Map.entry(CoreFlowStatus.REFUNDED, OrderStatus.REFUNDED)
	);

	/**
	 * Convert legacy OrderStatus to CoreFlowStatus
	 */
	public CoreFlowStatus toCore(OrderStatus legacyStatus) {
		if (legacyStatus == null) {
			return CoreFlowStatus.PENDING_PAYMENT; // default fallback
		}

		CoreFlowStatus coreStatus = LEGACY_TO_CORE_MAP.get(legacyStatus);
		if (coreStatus == null) {
			// Log warning for unknown mappings
			System.err.printf("WARNING: Unknown OrderStatus->CoreFlowStatus mapping for %s, defaulting to PENDING_PAYMENT%n",
				legacyStatus);
			return CoreFlowStatus.PENDING_PAYMENT;
		}

		return coreStatus;
	}

	/**
	 * Convert CoreFlowStatus to legacy OrderStatus for persistence/DTOs
	 */
	public OrderStatus toLegacy(CoreFlowStatus coreStatus) {
		if (coreStatus == null) {
			return OrderStatus.CREATED; // default fallback
		}

		OrderStatus legacyStatus = CORE_TO_LEGACY.get(coreStatus);
		if (legacyStatus == null) {
			// Log warning for unknown mappings
			System.err.printf("WARNING: Unknown CoreFlowStatus->OrderStatus mapping for %s, defaulting to CREATED%n",
				coreStatus);
			return OrderStatus.CREATED;
		}

		return legacyStatus;
	}

	/**
	 * Check if legacy status has a known core mapping
	 */
	public boolean isKnownLegacy(OrderStatus legacyStatus) {
		return LEGACY_TO_CORE_MAP.containsKey(legacyStatus);
	}

	/**
	 * Check if core status has a known legacy mapping
	 */
	public boolean isKnownCore(CoreFlowStatus coreStatus) {
		return CORE_TO_LEGACY.containsKey(coreStatus);
	}

	/**
	 * Get optional mapping to avoid fallbacks
	 */
	public Optional<CoreFlowStatus> toCoreOptional(OrderStatus legacyStatus) {
		return Optional.ofNullable(LEGACY_TO_CORE_MAP.get(legacyStatus));
	}

	/**
	 * Get optional mapping to avoid fallbacks
	 */
	public Optional<OrderStatus> toLegacyOptional(CoreFlowStatus coreStatus) {
		return Optional.ofNullable(CORE_TO_LEGACY.get(coreStatus));
	}
}