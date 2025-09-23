package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.domain.model.Product;
import com.github.spud.tinystore.order.infrastructure.acl.ProductClient;

import java.util.List;

import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/9/5
 */
@Service
public class ProductService {

    private ProductClient productClient;

    public List<Product> getProductsByIds(List<String> productIds) {
        productClient.getProductsByIds(productIds);
        return List.of();
    }

    public List<Product> getCouponsByIds(String userId, List<String> couponIds) {
        return List.of();
    }

}
