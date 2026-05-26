package com.github.spud.tinystore.inventory.interfaces.rest;

import com.github.spud.tinystore.inventory.application.service.InventoryReservationAppService;
import com.github.spud.tinystore.inventory.interfaces.dto.DeductRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.DeductResponse;
import com.github.spud.tinystore.inventory.interfaces.dto.InventoryConfirmRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.InventoryConfirmResponse;
import com.github.spud.tinystore.inventory.interfaces.dto.InventoryReleaseRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.InventoryReleaseResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Canonical inventory reservation controller.
 * <p>
 * Routes:
 * <ul>
 *   <li>POST /api/inventory/reservations/reserve  — Create PRE_DEDUCTED reservation</li>
 *   <li>POST /api/inventory/reservations/confirm  — Confirm on payment success (PRE_DEDUCTED → CONFIRMED)</li>
 *   <li>POST /api/inventory/reservations/release  — Release on order cancellation (PRE_DEDUCTED → RELEASED)</li>
 * </ul>
 * <p>
 * These are the canonical API endpoints for version 2 orders.
 * The legacy /api/inventory/deduct and /api/inventory/release remain available for backward compatibility.
 */
@RestController
@RequestMapping("/api/inventory/reservations")
@Validated
public class InventoryReservationController {

    private final InventoryReservationAppService appService;

    public InventoryReservationController(InventoryReservationAppService appService) {
        this.appService = appService;
    }

    /**
     * Reserve inventory for order creation.
     * Creates PRE_DEDUCTED reservations for all items.
     */
    @PostMapping("/reserve")
    public ResponseEntity<DeductResponse> reserve(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid DeductRequest request) {
        if (request.getTradeId() == null || request.getTradeId().isBlank()) {
            return ResponseEntity.badRequest().body(DeductResponse.fail(List.of(), "tradeId 不能为空"));
        }
        DeductResponse response = appService.reserve(idempotencyKey, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Confirm reservations on payment success.
     * Transitions PRE_DEDUCTED → CONFIRMED (terminal).
     * Idempotency-Key must be derived from paymentId.
     */
    @PostMapping("/confirm")
    public ResponseEntity<InventoryConfirmResponse> confirm(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid InventoryConfirmRequest request) {
        InventoryConfirmResponse response = appService.confirm(idempotencyKey, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Release reservations on order cancellation.
     * Transitions PRE_DEDUCTED → RELEASED (terminal).
     */
    @PostMapping("/release")
    public ResponseEntity<InventoryReleaseResponse> release(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid InventoryReleaseRequest request) {
        InventoryReleaseResponse response = appService.release(idempotencyKey, request);
        return ResponseEntity.ok(response);
    }
}
