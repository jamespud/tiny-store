package com.github.spud.tinystore.order.infrastructure.acl;

import com.github.spud.tinystore.order.interfaces.dto.Product;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author Spud
 * @date 2025/8/13
 */
@FeignClient("product-service")
public interface ProductClient {

	@GET
	@Path("/internal/restful/products")
	@Consumes(MediaType.APPLICATION_JSON)
	Product[] getProducts();

	@GET
	@Path("/internal/restful/products")
	@Consumes(MediaType.APPLICATION_JSON)
	List<Product> getProducts(List<String> productIds);

	@GET
	@Path("/internal/restful/coupons")
	@Consumes(MediaType.APPLICATION_JSON)
	List<Object> getCoupons(List<String> couponIds);


	List<Map<String, String>> getProductsByIds(List<String> productIds);

	@GET
	@Path("/internal/restful/sku/batch")
	@Consumes(MediaType.APPLICATION_JSON)
	InternalSkuBatchQueryResponse batchGetSkuInfo(
		@HeaderParam("X-Tenant-Id") String tenantId,
		@HeaderParam("X-User-Id") String userId,
		@QueryParam("skuIds") Set<String> skuIds,
		@HeaderParam("X-Trace-Id") String traceId
	);

	@Data
	class InternalSkuBatchQueryResponse {
		private Map<String, InternalSkuDTO> skuMap;
	}

	@Data
	class InternalSkuDTO {
		private String skuId;
		private String merchantId;
		private boolean available;
		private long promotePrice;
		private long unitPrice;
		private double weight;
		private String skuName;
		private String specJson;
	}
}
