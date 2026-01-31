package com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryStockEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaInventoryStockRepository extends JpaRepository<InventoryStockEntity, Long> {

	Optional<InventoryStockEntity> findByShopIdAndSkuId(String shopId, String skuId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from InventoryStockEntity s where s.shopId = :shopId and s.skuId = :skuId")
	Optional<InventoryStockEntity> findByShopIdAndSkuIdForUpdate(@Param("shopId") String shopId, @Param("skuId") String skuId);
}

