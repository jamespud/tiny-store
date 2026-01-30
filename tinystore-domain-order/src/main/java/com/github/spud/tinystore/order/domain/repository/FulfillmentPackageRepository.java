package com.github.spud.tinystore.order.domain.repository;

import com.github.spud.tinystore.order.domain.model.FulfillmentPackage;

import java.util.List;
import java.util.Optional;

/**
 * FulfillmentPackage 领域仓储接口
 */
public interface FulfillmentPackageRepository {
    
    FulfillmentPackage save(FulfillmentPackage pkg);
    
    Optional<FulfillmentPackage> findByPackageId(String packageId);
    
    List<FulfillmentPackage> findByTradeId(String tradeId);
}
