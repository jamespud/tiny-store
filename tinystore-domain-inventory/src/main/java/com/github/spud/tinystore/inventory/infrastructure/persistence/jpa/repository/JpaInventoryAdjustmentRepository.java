package com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryAdjustmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaInventoryAdjustmentRepository extends JpaRepository<InventoryAdjustmentEntity, Long> {

	boolean existsByReasonAndReferenceId(String reason, String referenceId);

	long countByReasonAndReferenceId(String reason, String referenceId);

	boolean existsByReasonAndReferenceIdAndShopIdAndSku(String reason, String referenceId,
	                                                    String shopId, String skuId);
}
