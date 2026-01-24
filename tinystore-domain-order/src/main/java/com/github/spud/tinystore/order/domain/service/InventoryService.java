package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.infrastructure.acl.InventoryClient;
import com.github.spud.tinystore.order.infrastructure.acl.InventoryClient.StockCommitRequest;
import com.github.spud.tinystore.order.infrastructure.acl.InventoryClient.StockCommitResponse;
import com.github.spud.tinystore.order.infrastructure.acl.InventoryClient.StockPreOccupyRequest;
import com.github.spud.tinystore.order.infrastructure.acl.InventoryClient.StockPreOccupyResponse;
import com.github.spud.tinystore.order.infrastructure.acl.InventoryClient.StockReleaseRequest;
import com.github.spud.tinystore.order.infrastructure.acl.InventoryClient.StockReleaseResponse;
import com.github.spud.tinystore.order.infrastructure.acl.InventoryClient.StockRestockRequest;
import com.github.spud.tinystore.order.infrastructure.acl.InventoryClient.StockRestockResponse;
import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/10/8
 */
@Service
public class InventoryService {

	private final InventoryClient inventoryClient;

	public InventoryService(InventoryClient inventoryClient) {
		this.inventoryClient = inventoryClient;
	}

	public StockPreOccupyResponse preOccupyStock(String idempotencyKey, StockPreOccupyRequest request) {
		return inventoryClient.preOccupy(idempotencyKey, request);
	}

	public StockReleaseResponse rollbackPreOccupy(String idempotencyKey, StockReleaseRequest request) {
		return inventoryClient.release(idempotencyKey, request);
	}

	public StockCommitResponse commitStock(String idempotencyKey, StockCommitRequest request) {
		return inventoryClient.commit(idempotencyKey, request);
	}

	public StockRestockResponse restockOnRefund(String idempotencyKey, StockRestockRequest request) {
		return inventoryClient.restock(idempotencyKey, request);
	}
}
