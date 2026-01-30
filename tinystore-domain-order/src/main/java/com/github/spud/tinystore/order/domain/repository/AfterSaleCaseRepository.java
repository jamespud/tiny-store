package com.github.spud.tinystore.order.domain.repository;

import com.github.spud.tinystore.order.domain.model.AfterSaleCase;

import java.util.List;
import java.util.Optional;

/**
 * AfterSaleCase 领域仓储接口
 */
public interface AfterSaleCaseRepository {
    
    AfterSaleCase save(AfterSaleCase saleCase);
    
    Optional<AfterSaleCase> findByCaseId(String caseId);
    
    Optional<AfterSaleCase> findByRefundId(String refundId);
    
    List<AfterSaleCase> findByTradeId(String tradeId);
    
    List<AfterSaleCase> findByOrderId(String orderId);
}
