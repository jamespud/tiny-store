package com.github.spud.tinystore.inventory.interfaces.rest;

import com.github.spud.tinystore.inventory.application.InventoryApplication;
import com.github.spud.tinystore.inventory.interfaces.dto.AdjustRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.AvailableQuery;
import com.github.spud.tinystore.inventory.interfaces.dto.AvailableResponse;
import com.github.spud.tinystore.inventory.interfaces.dto.ConfirmRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.ReleaseRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.ReserveRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.ReserveResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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