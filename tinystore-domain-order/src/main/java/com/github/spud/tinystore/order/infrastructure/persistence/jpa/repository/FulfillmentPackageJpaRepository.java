package com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.FulfillmentPackageEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * FulfillmentPackage JPA Repository
 */
@Repository
public interface FulfillmentPackageJpaRepository extends JpaRepository<FulfillmentPackageEntity, Long> {

    Optional<FulfillmentPackageEntity> findByPackageId(String packageId);

    List<FulfillmentPackageEntity> findByTradeId(String tradeId);

    List<FulfillmentPackageEntity> findBySellerId(String sellerId);
}
