package com.github.spud.tinystore.inventory.interfaces.rest;

import com.github.spud.tinystore.inventory.application.service.StockAppService;
import com.github.spud.tinystore.inventory.interfaces.dto.StockCommitRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.StockCommitResponse;
import com.github.spud.tinystore.inventory.interfaces.dto.StockPreOccupyRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.StockPreOccupyResponse;
import com.github.spud.tinystore.inventory.interfaces.dto.StockReleaseRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.StockReleaseResponse;
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

@RestController
@RequestMapping("/api/inventory/stock")
@Validated
public class StockController {

	private final StockAppService stockAppService;

	public StockController(StockAppService stockAppService) {
		this.stockAppService = stockAppService;
	}

	@PostMapping("/pre-occupy")
	public ResponseEntity<StockPreOccupyResponse> preOccupy(
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody @Valid StockPreOccupyRequest request
	) {
		return ResponseEntity.ok(stockAppService.preOccupy(idempotencyKey, request));
	}

	@PostMapping("/commit")
	public ResponseEntity<StockCommitResponse> commit(
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody @Valid StockCommitRequest request
	) {
		return ResponseEntity.ok(stockAppService.commit(idempotencyKey, request));
	}

	@PostMapping("/release")
	public ResponseEntity<StockReleaseResponse> release(
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody @Valid StockReleaseRequest request
	) {
		return ResponseEntity.ok(stockAppService.release(idempotencyKey, request));
	}

	@PostMapping("/restock")
	public ResponseEntity<StockRestockResponse> restock(
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody @Valid StockRestockRequest request
	) {
		return ResponseEntity.ok(stockAppService.restock(idempotencyKey, request));
	}
}

