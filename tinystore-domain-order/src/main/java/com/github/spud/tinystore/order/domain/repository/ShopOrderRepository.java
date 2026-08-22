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

    /**
     * 按 tradeId + inventoryStatus 查找子单（B1：再驱动未确认库存的子单）。
     */
    List<ShopOrder> findByTradeIdAndInventoryStatus(String tradeId, String inventoryStatus);
    
    Boolean saveAll(List<ShopOrder> orders);
}
