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

    /**
     * The longest reservation window this service is willing to hand out (review round-3 P1).
     *
     * <p>Must stay below {@code inventory.uncommit.orphan-check-delay}: the orphan reclaim treats a Redis
     * uncommit member older than that delay, with no DB row, as garbage -- which is only sound while every
     * legitimate reservation window has already closed. Enforcing it *here*, at the request boundary, makes
     * that independent of how the order service is configured.
     */
    @Value("${inventory.uncommit.max-reservation-ttl:PT15M}")
    private java.time.Duration maxReservationTtl;

    public InventoryReservationAppService(InventoryReservationDomainService domainService) {
        this.domainService = domainService;
    }

    /**
     * Configuration invariant: the service's own default expiry must also fit the orphan budget, otherwise a
     * caller that omits the TTL gets a reservation the cleaner could consider reclaimable while still open.
     */
    @jakarta.annotation.PostConstruct
    void validateTtlBudget() {
        java.time.Duration ownDefault = java.time.Duration.ofMinutes(defaultExpiryMinutes);
        if (ownDefault.compareTo(maxReservationTtl) > 0) {
            throw new IllegalStateException(String.format(
                    "inventory.reservation.expiry.default-minutes=%d exceeds "
                            + "inventory.uncommit.max-reservation-ttl=%s: a caller that does not state a TTL "
                            + "would get a reservation longer than the orphan-reclaim budget",
                    defaultExpiryMinutes, maxReservationTtl));
        }
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
            // Round-3 P1: enforce the cross-service TTL contract at the boundary, BEFORE Redis is touched.
            // The order service carries its own reservation TTL; if a deployment ever raises it above this
            // service's max-reservation-ttl, the orphan reclaim would be able to release a pre-deduction whose
            // DB row has not been written yet (oversell) -- the two services read different properties, so a
            // runtime check is the only thing that keeps them honest.
            OffsetDateTime expireAt = resolveRequestedExpireAt(request);
            InventoryReserveCommand command = InventoryReserveCommand.builder()
                    .idempotencyKey(idempotencyKey)
                    .orderId(request.getOrderId())
                    .tradeId(request.getTradeId())
                    .traceId(MDC.get(LogConstant.MDC_LOG_ID))
                    .expireAt(expireAt)
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
     * Resolves the reservation window for a pre-deduct request.
     *
     * @throws IllegalArgumentException when the caller asks for a non-positive TTL, or one larger than
     *         {@code inventory.uncommit.max-reservation-ttl}
     */
    private OffsetDateTime resolveRequestedExpireAt(DeductRequest request) {
        Long requestedMinutes = request.getReservationTtlMinutes();
        if (requestedMinutes == null) {
            return OffsetDateTime.now().plusMinutes(defaultExpiryMinutes);
        }
        if (requestedMinutes <= 0) {
            throw new IllegalArgumentException(
                    "reservationTtlMinutes must be positive, got " + requestedMinutes);
        }
        java.time.Duration requested = java.time.Duration.ofMinutes(requestedMinutes);
        if (requested.compareTo(maxReservationTtl) > 0) {
            throw new IllegalArgumentException(String.format(
                    "requested reservation TTL %s exceeds inventory.uncommit.max-reservation-ttl=%s: the "
                            + "orphan reclaim could release a pre-deduction whose reservation row has not been "
                            + "written yet",
                    requested, maxReservationTtl));
        }
        return OffsetDateTime.now().plus(requested);
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
