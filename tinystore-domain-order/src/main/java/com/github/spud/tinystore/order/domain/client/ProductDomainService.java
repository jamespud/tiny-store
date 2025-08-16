package com.github.spud.tinystore.order.domain.client;

import com.github.spud.tinystore.order.api.dto.Product;
import com.github.spud.tinystore.order.api.dto.Settlement;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author Spud
 * @date 2025/8/13
 */
@FeignClient("product-service")
public interface ProductDomainService {

	default void replenishProductInformation(Settlement bill) {
		bill.productMap = Stream.of(getProducts())
			.collect(Collectors.toMap(Product::getProductId, Function.identity()));
	}

	@GET
	@Path("/internal/restful/products")
	@Consumes(MediaType.APPLICATION_JSON)
	Product[] getProducts();
}
