package com.github.spud.tinystore.inventory.infrastructure.adapter;

import com.github.spud.tinystore.inventory.domain.port.InventoryReconciliationPort;
import com.github.spud.tinystore.inventory.domain.value.ReconciliationSnapshot;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryStockEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryReservationRepository;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryStockRepository;
import com.github.spud.tinystore.inventory.infrastructure.util.InventoryRedisManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
public class JpaInventoryReconciliationAdapter implements InventoryReconciliationPort {

    private final JpaInventoryStockRepository stockRepository;
    private final JpaInventoryReservationRepository reservationRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final InventoryRedisManager redisManager;

    public JpaInventoryReconciliationAdapter(JpaInventoryStockRepository stockRepository,
                                              JpaInventoryReservationRepository reservationRepository,
                                              RedisTemplate<String, Object> redisTemplate,
                                              InventoryRedisManager redisManager) {
        this.stockRepository = stockRepository;
        this.reservationRepository = reservationRepository;
        this.redisTemplate = redisTemplate;
        this.redisManager = redisManager;
    }

    @Override
    public ReconciliationSnapshot snapshot(String shopId, String skuId) {
        long dbTotal = stockRepository.findByShopIdAndSkuId(shopId, skuId)
                .map(InventoryStockEntity::getTotalQuantity).orElse(0L);
        long dbConfirmed = reservationRepository.sumQuantityByShopSkuStatus(shopId, skuId, "CONFIRMED");
        long dbPreDeducted = reservationRepository.sumQuantityByShopSkuStatus(shopId, skuId, "PRE_DEDUCTED");

        Long redisTotal = readLong(redisManager.getTotalKeyV2(shopId, skuId));
        Long redisDeducted = readLong(redisManager.getDeductedKeyV2(shopId, skuId));

        return ReconciliationSnapshot.builder()
                .shopId(shopId).skuId(skuId)
                .dbTotalQuantity(dbTotal)
                .dbConfirmedQuantity(dbConfirmed)
                .dbPreDeductedQuantity(dbPreDeducted)
                .redisTotal(redisTotal)
                .redisDeducted(redisDeducted)
                .build();
    }

    private Long readLong(String key) {
        try {
            Object v = redisTemplate.opsForValue().get(key);
            if (v == null) return null;
            return Long.parseLong(v.toString());
        } catch (Exception e) {
            log.warn("Failed to read Redis key {}: {}", key, e.getMessage());
            return null;
        }
    }
}
