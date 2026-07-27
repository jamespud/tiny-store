package com.github.spud.tinystore.inventory.interfaces.rest;

import com.github.spud.tinystore.inventory.application.service.InventoryAdjustmentAppService;
import com.github.spud.tinystore.inventory.interfaces.dto.InventoryAdjustRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.InventoryAdjustResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory/adjustments")
@Validated
public class InventoryAdjustmentController {

    private final InventoryAdjustmentAppService appService;

    public InventoryAdjustmentController(InventoryAdjustmentAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public ResponseEntity<InventoryAdjustResponse> adjust(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid InventoryAdjustRequest request) {
        return ResponseEntity.ok(appService.adjust(idempotencyKey, request));
    }
}
