package com.github.spud.tinystore.inventory.interfaces.rest;

import com.github.spud.tinystore.inventory.application.service.StockAppService;
import com.github.spud.tinystore.inventory.interfaces.dto.StockRestockRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.StockRestockResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存补货控制器（新接口）
 */
@RestController
@RequestMapping("/api/inventory")
@Validated
public class InventoryRestockController {

    private final StockAppService stockAppService;

    public InventoryRestockController(StockAppService stockAppService) {
        this.stockAppService = stockAppService;
    }

    @PostMapping("/restock")
    public ResponseEntity<StockRestockResponse> restock(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid StockRestockRequest request) {
        return ResponseEntity.ok(stockAppService.restock(idempotencyKey, request));
    }
}
