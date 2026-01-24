package com.github.spud.tinystore.order.infrastructure.acl;

import java.util.List;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "inventory-service", path = "/api/inventory/stock")
public interface InventoryClient {

	@PostMapping("/pre-occupy")
	StockPreOccupyResponse preOccupy(@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody StockPreOccupyRequest request);

	@PostMapping("/commit")
	StockCommitResponse commit(@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody StockCommitRequest request);

	@PostMapping("/release")
	StockReleaseResponse release(@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody StockReleaseRequest request);

	@PostMapping("/restock")
	StockRestockResponse restock(@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody StockRestockRequest request);

	@Data
	class StockPreOccupyRequest {
		private String tenantId;
		private String orderNo;
		private Long expiresAtEpochMs;
		private List<Line> lines;

		@Data
		public static class Line {
			private String skuId;
			private Integer quantity;
		}
	}

	@Data
	class StockPreOccupyResponse {
		private boolean success;
		private List<String> preOccupyIds;
		private List<String> lackSkuIds;
		private long expiresAtEpochMs;
		private String message;
	}

	@Data
	class StockCommitRequest {
		private String tenantId;
		private String orderNo;
		private String payNo;
		private Long paidAtEpochMs;
		private List<String> preOccupyIds;
	}

	@Data
	class StockCommitResponse {
		private boolean success;
		private String message;
	}

	@Data
	class StockReleaseRequest {
		private String tenantId;
		private String orderNo;
		private String reason;
		private List<String> preOccupyIds;
	}

	@Data
	class StockReleaseResponse {
		private boolean success;
		private String message;
	}

	@Data
	class StockRestockRequest {
		private String tenantId;
		private String orderNo;
		private String refundId;
		private List<Item> items;

		@Data
		public static class Item {
			private String skuId;
			private Integer quantity;
		}
	}

	@Data
	class StockRestockResponse {
		private boolean success;
		private String message;
	}
}

