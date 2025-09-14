package com.github.spud.tinystore.product.domain.service;

import com.github.spud.tinystore.product.domain.model.Stock;
import org.springframework.stereotype.Service;

/**
 * 领域服务：库存可用性相关逻辑占位
 */
@Service
public class StockDomainService {
	// TODO(inv): 根据实际业务调整阈值语义（占位）
	public static final int NEGATIVE_AVAILABLE_GUARD = 0;

	public boolean validateAvailability(Stock stock, int reqQuantity) {
		// TODO(inv): 判断 available 是否足够 (available = total - reserved)；当前占位直接返回 false
		return false;
	}
	public int computeAvailable(Stock stock) {
		// TODO(inv): available = total - reserved；当前占位返回 0
		return 0;
	}
}
