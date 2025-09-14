package com.github.spud.tinystore.product.interfaces.rest;

import com.github.spud.tinystore.product.application.InventoryApplication;
import com.github.spud.tinystore.product.interfaces.dto.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 库存接口（仅方法签名占位，无实现逻辑）
 */
@RestController
@RequestMapping("/inventory")
public class StockController {

	@Autowired
	private InventoryApplication inventoryApplication;

	@PostMapping("/reserve")
	public ReserveResponse reserve(@RequestBody ReserveRequest request) {
		return new ReserveResponse();
	}

	@PostMapping("/confirm")
	public void confirm(@RequestBody ConfirmRequest request) {
	}

	@PostMapping("/release")
	public void release(@RequestBody ReleaseRequest request) {
	}

	@PostMapping("/adjust")
	public void adjust(@RequestBody AdjustRequest request) {
	}

	@GetMapping("/available")
	public AvailableResponse available(@RequestParam String shopId, @RequestParam String skuId) {
		return new AvailableResponse();
	}

	@PostMapping("/available/batch")
	public List<AvailableResponse> batchAvailable(@RequestBody List<AvailableQuery> queries) {
		return List.of();
	}
}
