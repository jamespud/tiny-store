package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.infrastructure.acl.InventoryClient.StockPreOccupyRequest;
import com.github.spud.tinystore.order.infrastructure.acl.InventoryClient.StockPreOccupyResponse;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/10/8
 */
@Service
public class InventoryService {

	public StockPreOccupyResponse preOccupyStock(List<StockPreOccupyRequest> request) {
		return null;
	}

	public void rollbackPreOccupy(List<String> preOccupyIds) {

	}
}
