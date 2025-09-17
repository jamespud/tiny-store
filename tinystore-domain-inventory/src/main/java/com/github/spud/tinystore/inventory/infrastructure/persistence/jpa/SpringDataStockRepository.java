package com.github.spud.tinystore.inventory.infrastructure.persistence.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataStockRepository extends JpaRepository<StockEntity, Long> {

	Optional<StockEntity> findByShopIdAndSkuId(String shopId, String skuId);
}

