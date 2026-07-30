package com.github.spud.tinystore.order.infrastructure.acl;

import com.github.spud.tinystore.order.infrastructure.acl.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Inventory 域 RPC 客户端
 */
@FeignClient(name = "tinystore-inventory-service")
public interface InventoryClient {

    // ==================== Canonical Reservation API (RFC-001) ====================

    /**
     * Canonical: adjust inventory (signed delta). Refund = reason RESTOCK_REFUND, delta = +qty.
     * Route: POST /api/inventory/adjustments
     */
    @PostMapping("/api/inventory/adjustments")
    InventoryAdjustResponse adjust(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody InventoryAdjustRequest request);

    // ==================== Canonical Reservation API (RFC-001) ====================

    /**
     * Canonical: Reserve inventory (creates PRE_DEDUCTED reservations).
     * Route: POST /api/inventory/reservations/reserve
     */
    @PostMapping("/api/inventory/reservations/reserve")
    InventoryDeductResponse reserveCanonical(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody InventoryDeductRequest request);

    /**
     * Canonical: Confirm reservations on payment success (PRE_DEDUCTED → CONFIRMED).
     * Route: POST /api/inventory/reservations/confirm
     */
    @PostMapping("/api/inventory/reservations/confirm")
    InventoryConfirmResponse confirmReservation(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody InventoryConfirmRequest request);

    /**
     * Canonical: Release reservations on order cancellation (PRE_DEDUCTED → RELEASED).
     * Route: POST /api/inventory/reservations/release
     */
    @PostMapping("/api/inventory/reservations/release")
    InventoryReleaseResponseV2 releaseCanonical(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody InventoryReleaseRequestV2 request);

    /**
     * Redis preDeduct only (sync, for async reserve split). Returns reservationId without DB write.
     * Route: POST /api/inventory/reservations/pre-deduct
     */
    @PostMapping("/api/inventory/reservations/pre-deduct")
    InventoryDeductResponse preDeductRedisOnly(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody InventoryDeductRequest request);

    /**
     * Redis rollback only (for createTrade compensation). No DB.
     * Route: POST /api/inventory/reservations/rollback
     */
    @PostMapping("/api/inventory/reservations/rollback")
    InventoryDeductResponse rollbackRedis(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody java.util.Map<String, String> body);
}
