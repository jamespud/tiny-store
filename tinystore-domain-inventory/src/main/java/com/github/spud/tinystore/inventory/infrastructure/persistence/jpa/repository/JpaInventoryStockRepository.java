package com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryStockEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaInventoryStockRepository extends JpaRepository<InventoryStockEntity, Long> {

	Optional<InventoryStockEntity> findByTenantIdAndSkuId(String tenantId, String skuId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from InventoryStockEntity s where s.tenantId = :tenantId and s.skuId = :skuId")
	Optional<InventoryStockEntity> findByTenantIdAndSkuIdForUpdate(@Param("tenantId") String tenantId, @Param("skuId") String skuId);
}

