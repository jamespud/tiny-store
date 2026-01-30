package com.github.spud.tinystore.order.domain.repository;

import com.github.spud.tinystore.order.domain.model.Trade;

import java.util.Optional;

/**
 * Trade 领域仓储接口
 */
public interface TradeRepository {
    
    Trade save(Trade trade);
    
    Optional<Trade> findByTradeId(String tradeId);
    
    void delete(Trade trade);
}
