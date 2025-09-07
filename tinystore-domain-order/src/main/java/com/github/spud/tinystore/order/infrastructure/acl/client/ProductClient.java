package com.github.spud.tinystore.order.infrastructure.acl.client;

import com.github.spud.tinystore.order.interfaces.dto.Product;
import com.github.spud.tinystore.order.interfaces.dto.Settlement;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author Spud
 * @date 2025/8/13
 */
@FeignClient("product-service")
public interface ProductClient {

	default void replenishProductInformation(Settlement bill) {
		bill.productMap = Stream.of(getProducts())
			.collect(Collectors.toMap(Product::getSpuId, Function.identity()));
	}

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
}
