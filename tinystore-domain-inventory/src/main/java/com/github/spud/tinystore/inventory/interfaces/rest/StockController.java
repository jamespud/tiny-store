package com.github.spud.tinystore.inventory.interfaces.rest;

import com.github.spud.tinystore.inventory.application.service.StockAppService;
import com.github.spud.tinystore.inventory.interfaces.dto.*;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存旧接口控制器（DB 预占 + Redis reserve 链路）
 *
 * @deprecated 使用 {@link InventoryDeductController} 替代（POST /api/inventory/deduct 和 /api/inventory/release）
 *             本控制器通过 inventory.legacy-stock-api-enabled 控制开关（默认 true）。
 *             Batch-3 上线后将此值改为 false，关闭旧 API。
 */
@Deprecated
@RestController
@RequestMapping("/api/inventory/stock")
@Validated
@ConditionalOnProperty(name = "inventory.legacy-stock-api-enabled", havingValue = "true", matchIfMissing = true)
public class StockController {

	private final StockAppService stockAppService;

	public StockController(StockAppService stockAppService) {
		this.stockAppService = stockAppService;
	}

    /**
     * @deprecated 使用 POST /api/inventory/deduct
     */
    @Deprecated
    @PostMapping("/reserve")
    public ResponseEntity<StockPreOccupyResponse> reserve(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody @Valid StockReserveRequest request
    ) {
        return ResponseEntity.ok(stockAppService.reserve(idempotencyKey, request));
    }

	/**
	 * @deprecated 使用 POST /api/inventory/deduct
	 */
	@Deprecated
	@PostMapping("/pre-occupy")
	public ResponseEntity<StockPreOccupyResponse> preOccupy(
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody @Valid StockPreOccupyRequest request
	) {
		return ResponseEntity.ok(stockAppService.preOccupy(idempotencyKey, request));
	}

	/**
	 * @deprecated 不再需要两步提交，使用 POST /api/inventory/deduct 替代。
	 *             此接口与 canonical confirm 语义冲突，Batch-1 后应优先下线。
	 */
	@Deprecated
	@PostMapping("/commit")
	public ResponseEntity<StockCommitResponse> commit(
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody @Valid StockCommitRequest request
	) {
		return ResponseEntity.ok(stockAppService.commit(idempotencyKey, request));
	}

	/**
	 * @deprecated 使用 POST /api/inventory/release
	 */
	@Deprecated
	@PostMapping("/release")
	public ResponseEntity<StockReleaseResponse> release(
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody @Valid StockReleaseRequest request
	) {
		return ResponseEntity.ok(stockAppService.release(idempotencyKey, request));
	}
}
