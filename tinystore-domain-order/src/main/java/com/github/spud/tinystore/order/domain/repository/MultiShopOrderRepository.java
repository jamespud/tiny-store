package com.github.spud.tinystore.order.domain.repository;

import com.github.spud.tinystore.order.domain.model.MainOrder;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author Spud
 * @date 2025/10/5
 */
@FeignClient
public interface MultiShopOrderRepository {

	void saveMultiShopOrder(MainOrder mainOrder);
}
