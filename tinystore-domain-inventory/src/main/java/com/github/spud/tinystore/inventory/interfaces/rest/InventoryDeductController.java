package com.github.spud.tinystore.inventory.interfaces.rest;

import com.github.spud.tinystore.inventory.application.service.InventoryDeductAppService;
import com.github.spud.tinystore.inventory.interfaces.dto.DeductRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.DeductResponse;
import com.github.spud.tinystore.inventory.interfaces.dto.InventoryReleaseRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.InventoryReleaseResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 库存扣减/释放控制器（V2 新接口）
 * <p>
 * 路由前缀：/api/inventory
 * <ul>
 *   <li>POST /deduct  — Redis 原子扣减，返回按 SKU 的 occupyPairs</li>
 *   <li>POST /release — 按 SKU 回滚（shopId + skuId + occupyId）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/inventory")
@Validated
public class InventoryDeductController {

    private final InventoryDeductAppService appService;

    public InventoryDeductController(InventoryDeductAppService appService) {
        this.appService = appService;
    }

    @PostMapping("/deduct")
    public ResponseEntity<DeductResponse> deduct(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid DeductRequest request) {
        DeductResponse response = appService.deduct(idempotencyKey, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/release")
    public ResponseEntity<InventoryReleaseResponse> release(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid InventoryReleaseRequest request) {
        InventoryReleaseResponse response = appService.release(idempotencyKey, request);
        return ResponseEntity.ok(response);
    }
}
