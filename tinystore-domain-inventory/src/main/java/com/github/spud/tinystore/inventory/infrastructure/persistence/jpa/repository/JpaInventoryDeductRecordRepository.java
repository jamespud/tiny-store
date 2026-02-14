package com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryDeductRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JpaInventoryDeductRecordRepository extends JpaRepository<InventoryDeductRecordEntity, Long> {

    List<InventoryDeductRecordEntity> findByOrderId(String orderId);

    List<InventoryDeductRecordEntity> findByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query("UPDATE InventoryDeductRecordEntity r SET r.status = :status, r.releaseReason = :reason " +
           "WHERE r.shopId = :shopId AND r.skuId = :skuId AND r.occupyId = :occupyId AND r.status = 'DEDUCTED'")
    int markReleased(@Param("shopId") String shopId,
                     @Param("skuId") String skuId,
                     @Param("occupyId") String occupyId,
                     @Param("status") String status,
                     @Param("reason") String reason);
}
