package com.github.spud.tinystore.order.infrastructure.acl;

import com.github.spud.tinystore.order.interfaces.dto.Product;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

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
}
