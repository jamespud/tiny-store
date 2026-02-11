package com.github.spud.tinystore.order.domain.repository;

import com.github.spud.tinystore.order.domain.model.ShopOrder;

import java.util.List;
import java.util.Optional;

/**
 * ShopOrder 领域仓储接口
 */
public interface ShopOrderRepository {
    
    ShopOrder save(ShopOrder order);
    
    Optional<ShopOrder> findByOrderId(String orderId);
    
    List<ShopOrder> findByTradeId(String tradeId);
    
    Boolean saveAll(List<ShopOrder> orders);
}
