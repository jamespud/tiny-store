package com.github.spud.tinystore.order.infrastructure.acl;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * @author Spud
 * @date 2025/10/8
 */

public interface InventoryClient {

	@Builder
	@AllArgsConstructor
	class StockPreOccupyRequest{
		private String skuId;
		private Integer quantity;
	}
	
	@Data
	class StockPreOccupyResponse {
		private boolean success;
		private Object lackSkuId;
		private List<String> preOccupyIds;
	}
}
