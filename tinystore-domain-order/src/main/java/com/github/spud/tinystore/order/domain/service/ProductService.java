package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.infrastructure.acl.client.ProductClient;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/9/5
 */
@Service
public class ProductService {
	
	private ProductClient productClient;

	public List<Object> getProductsByIds(List<String> productIds) {
		productClient.getProductsByIds(productIds);
		return List.of();
	}

	public List<Object> getCouponsByIds(String userId, List<String> couponIds) {
		return List.of();
	}

}
