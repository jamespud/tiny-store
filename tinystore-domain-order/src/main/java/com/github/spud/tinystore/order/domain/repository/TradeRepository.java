package com.github.spud.tinystore.order.domain.repository;

import com.github.spud.tinystore.order.domain.model.Trade;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Trade 领域仓储接口
 */
public interface TradeRepository {

    Trade save(Trade trade);

    Optional<Trade> findByTradeId(String tradeId);

    void delete(Trade trade);

    /**
     * 查找 promotion commit 仍为 PENDING、未支付且创建早于阈值的 tradeId 列表
     * （PENDING 超时兜底调度器使用）。
     */
    List<String> findStalePendingCommit(LocalDateTime threshold);
}
