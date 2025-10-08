package com.github.spud.tinystore.order.infrastructure.acl;

import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author Spud
 * @date 2025/10/5
 */
@FeignClient
public interface InventoryFeignClient {

	StockPreOccupyResponse preOccupyStock(List<StockPreOccupyDTO> preOccupyList);

	void rollbackPreOccupy(List<String> preOccupyIds);

	class StockPreOccupyResponse {

		private boolean success;
		private Object lackSkuId;
		private List<String> preOccupyIds;

		public boolean isSuccess() {
			return success;
		}

		public void setSuccess(boolean success) {
			this.success = success;
		}

		public Object getLackSkuId() {
			return lackSkuId;
		}

		public void setLackSkuId(Object lackSkuId) {
			this.lackSkuId = lackSkuId;
		}

		public List<String> getPreOccupyIds() {
			return preOccupyIds;
		}

		public void setPreOccupyIds(List<String> preOccupyIds) {
			this.preOccupyIds = preOccupyIds;
		}
	}

	@AllArgsConstructor
	class StockPreOccupyDTO {

		private String skuId;
		private Integer quantity;
	}

}
