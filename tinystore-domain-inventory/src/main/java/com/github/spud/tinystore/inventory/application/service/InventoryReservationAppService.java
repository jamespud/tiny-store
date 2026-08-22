package com.github.spud.tinystore.inventory.application.service;

import com.github.spud.tinystore.inventory.domain.command.InventoryConfirmCommand;
import com.github.spud.tinystore.inventory.domain.command.InventoryReleaseCommand;
import com.github.spud.tinystore.inventory.domain.command.InventoryReserveCommand;
import com.github.spud.tinystore.inventory.domain.service.InventoryReservationDomainService;
import com.github.spud.tinystore.inventory.domain.value.OccupyPair;
import com.github.spud.tinystore.inventory.domain.value.ReservationRef;
import com.github.spud.tinystore.inventory.domain.value.ReservationResult;
import com.github.spud.tinystore.inventory.interfaces.dto.DeductRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.DeductResponse;
import com.github.spud.tinystore.inventory.interfaces.dto.InventoryConfirmRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.InventoryConfirmResponse;
import com.github.spud.tinystore.inventory.interfaces.dto.InventoryReleaseRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.InventoryReleaseResponse;
import com.github.spud.tinystore.interfaces.aspect.LogConstant;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Canonical inventory reservation application service.
 * <p>
 * This is the single canonical orchestration entry point for all reservation
 * lifecycle operations.
 * It owns reserve / confirm / release / expireExpiredReservations.
 * <p>
 * InventoryDeductAppService delegates here when canonical-enabled flag is on.
 */
@Slf4j
@Service
public class InventoryReservationAppService {

    private final InventoryReservationDomainService domainService;

    @Value("${inventory.reservation.expiry.default-minutes:15}")
    private int defaultExpiryMinutes;

    public InventoryReservationAppService(InventoryReservationDomainService domainService) {
        this.domainService = domainService;
    }

    // ========================== Reserve ==========================

    /**
     * Reserve inventory (canonical path, called for version 2 orders).
     * Maps DeductRequest → InventoryReserveCommand for compatibility.
     */
    public DeductResponse reserve(String idempotencyKey, DeductRequest request) {
        MDC.put("orderId", request.getOrderId());
        try {
            InventoryReserveCommand command = InventoryReserveCommand.builder()
                    .idempotencyKey(idempotencyKey)
                    .orderId(request.getOrderId())
                    .tradeId(request.getTradeId())
                    .traceId(MDC.get(LogConstant.MDC_LOG_ID))
                    .expireAt(OffsetDateTime.now().plusMinutes(defaultExpiryMinutes))
                    .items(request.getItems().stream()
                            .map(item -> InventoryReserveCommand.Item.builder()
                                    .shopId(item.getShopId())
                                    .skuId(item.getSkuId())
                                    .quantity(item.getQuantity())
                                    .build())
                            .toList())
                    .build();

            ReservationResult result = domainService.reserve(command);

            if (!result.isSuccess()) {
                return DeductResponse.fail(result.getLackSkuIds(), result.getMessage());
            }

            if (result.getReservationRefs() == null || result.getReservationRefs().isEmpty()) {
                log.error("Canonical reserve returned success without reservation refs: tradeId={}, orderId={}",
                        request.getTradeId(), request.getOrderId());
                return DeductResponse.fail(List.of(), "RESERVATION_REFS_MISSING");
            }

            List<DeductResponse.OccupyPairDto> pairs = result.getReservationRefs().stream()
                    .map(ref -> DeductResponse.OccupyPairDto.builder()
                            .shopId(ref.getShopId())
                            .skuId(ref.getSkuId())
                            .occupyId(ref.getReservationId())
                            .build())
                    .toList();
            return DeductResponse.ok(pairs);
        } finally {
            MDC.remove("orderId");
        }
    }

    // ========================== Confirm ==========================

    /**
     * Confirm reservations on payment success.
     * Idempotency key must be derived from paymentId by the caller.
     */
    public InventoryConfirmResponse confirm(String idempotencyKey, InventoryConfirmRequest request) {
        MDC.put("orderId", request.getOrderId());
        MDC.put("paymentId", request.getPaymentId());
        try {
            InventoryConfirmCommand command = InventoryConfirmCommand.builder()
                    .idempotencyKey(idempotencyKey)
                    .paymentId(request.getPaymentId())
                    .tradeId(request.getTradeId())
                    .orderId(request.getOrderId())
                    .traceId(request.getTraceId())
                    .occupyPairs(request.getOccupyPairs().stream()
                            .map(p -> OccupyPair.builder()
                                    .shopId(p.getShopId())
                                    .skuId(p.getSkuId())
                                    .occupyId(p.getOccupyId())
                                    .build())
                            .toList())
                    .build();

            ReservationResult result = domainService.confirm(command);

            if (!result.isSuccess()) {
                return InventoryConfirmResponse.conflict(result.getConflictReservationIds(),
                        result.getMessage());
            }

            List<InventoryConfirmResponse.ReservationRefDto> refs = result.getReservationRefs().stream()
                    .map(ref -> InventoryConfirmResponse.ReservationRefDto.builder()
                            .shopId(ref.getShopId())
                            .skuId(ref.getSkuId())
                            .reservationId(ref.getReservationId())
                            .build())
                    .toList();
            return InventoryConfirmResponse.ok(refs);
        } finally {
            MDC.remove("orderId");
            MDC.remove("paymentId");
        }
    }

    // ========================== Release ==========================

    /**
     * Release reservations on order cancellation.
     */
    public InventoryReleaseResponse release(String idempotencyKey, InventoryReleaseRequest request) {
        MDC.put("orderId", request.getOrderId());
        try {
            InventoryReleaseCommand command = InventoryReleaseCommand.builder()
                    .idempotencyKey(idempotencyKey)
                    .orderId(request.getOrderId())
                    .reason(request.getReason())
                    .occupyPairs(request.getOccupyPairs().stream()
                            .map(p -> OccupyPair.builder()
                                    .shopId(p.getShopId())
                                    .skuId(p.getSkuId())
                                    .occupyId(p.getOccupyId())
                                    .build())
                            .toList())
                    .build();

            ReservationResult result = domainService.release(command);

            return InventoryReleaseResponse.builder()
                    .success(result.isSuccess())
                    .message(result.getMessage())
                    .build();
        } finally {
            MDC.remove("orderId");
        }
    }

    // ========================== Pre-Deduct (Redis only, async split) ==========================

    /**
     * Redis preDeduct only (sync, for async reserve). Returns reservationId without DB write.
     */
    public DeductResponse preDeductRedisOnly(String idempotencyKey, DeductRequest request) {
        MDC.put("orderId", request.getOrderId());
        try {
            InventoryReserveCommand command = InventoryReserveCommand.builder()
                    .idempotencyKey(idempotencyKey)
                    .orderId(request.getOrderId())
                    .tradeId(request.getTradeId())
                    .traceId(MDC.get(LogConstant.MDC_LOG_ID))
                    .expireAt(OffsetDateTime.now().plusMinutes(defaultExpiryMinutes))
                    .items(request.getItems().stream()
                            .map(item -> InventoryReserveCommand.Item.builder()
                                    .shopId(item.getShopId()).skuId(item.getSkuId()).quantity(item.getQuantity()).build())
                            .toList())
                    .build();

            ReservationResult result = domainService.reserveRedisOnly(command);

            if (!result.isSuccess()) {
                return DeductResponse.fail(result.getLackSkuIds(), result.getMessage());
            }
            if (result.getReservationRefs() == null || result.getReservationRefs().isEmpty()) {
                log.error("preDeductRedisOnly returned success without refs: tradeId={}, orderId={}",
                        request.getTradeId(), request.getOrderId());
                return DeductResponse.fail(List.of(), "RESERVATION_REFS_MISSING");
            }

            List<DeductResponse.OccupyPairDto> pairs = result.getReservationRefs().stream()
                    .map(ref -> DeductResponse.OccupyPairDto.builder()
                            .shopId(ref.getShopId()).skuId(ref.getSkuId()).occupyId(ref.getReservationId()).build())
                    .toList();
            return DeductResponse.ok(pairs);
        } finally {
            MDC.remove("orderId");
        }
    }

    /**
     * Redis rollback only (for createTrade compensation). No DB.
     */
    public boolean rollbackRedis(String shopId, String skuId, String reservationId) {
        return domainService.rollbackRedis(shopId, skuId, reservationId);
    }

    // ========================== Expire ==========================

    /**
     * Expire overdue PRE_DEDUCTED reservations.
     * Called by ReservationExpiryTask only.
     *
     * @return count of expired reservations
     */
    public int expireExpiredReservations() {
        return domainService.expireExpiredReservations(OffsetDateTime.now());
    }
}
