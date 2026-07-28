package com.github.spud.tinystore.inventory.infrastructure.adapter;

import com.github.spud.tinystore.inventory.domain.port.InventoryDeductGateway;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryStockEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryStockRepository;
import com.github.spud.tinystore.inventory.infrastructure.util.InventoryRedisManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis 库存扣减网关（基础设施层适配器）
 * <p>
 * 实现领域端口 {@link InventoryDeductGateway}，领域层不感知 Redis key / Lua / DB 回源等技术细节。
 */
@Slf4j
@Component
public class RedisInventoryDeductGateway implements InventoryDeductGateway {

    private final InventoryRedisManager redisManager;
    private final JpaInventoryStockRepository stockRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public RedisInventoryDeductGateway(InventoryRedisManager redisManager,
                                       JpaInventoryStockRepository stockRepository,
                                       RedisTemplate<String, Object> redisTemplate) {
        this.redisManager = redisManager;
        this.stockRepository = stockRepository;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<String> preDeduct(String shopId, String skuId, int qty, String orderId) {
        // 确保 total key 已初始化（DB 回源 SETNX）
        ensureTotalKeyInitialized(shopId, skuId);

        String member = redisManager.preDeductInventoryV2(shopId, skuId, qty, orderId);
        return Optional.ofNullable(member);
    }

    @Override
    public boolean rollback(String shopId, String skuId, String occupyId) {
        return redisManager.rollbackPreDeductV2(shopId, skuId, occupyId);
    }

    @Override
    public boolean addTotal(String shopId, String skuId, long delta) {
        // Runs AFTER the DB tx committed, so DB total_quantity already includes this delta.
        String totalKey = redisManager.getTotalKeyV2(shopId, skuId);
        Boolean exists = redisTemplate.hasKey(totalKey);
        if (!Boolean.TRUE.equals(exists)) {
            // Key absent: initialize to authoritative DB total (already post-adjustment).
            // Do NOT then INCRBY - that would double-count the delta.
            long dbTotal = 0;
            try {
                Optional<InventoryStockEntity> stockOpt = stockRepository.findByShopIdAndSkuId(shopId, skuId);
                if (stockOpt.isPresent()) {
                    dbTotal = stockOpt.get().getTotalQuantity();
                } else {
                    log.warn("DB inventory_stock not found for addTotal init: shopId={}, skuId={}, using 0", shopId, skuId);
                }
            } catch (Exception e) {
                log.error("Failed to load totalQuantity from DB for addTotal init: shopId={}, skuId={}", shopId, skuId, e);
            }
            Boolean set = redisTemplate.opsForValue().setIfAbsent(totalKey, String.valueOf(dbTotal));
            if (Boolean.TRUE.equals(set)) {
                log.info("Initialized Redis total key on adjust: {}={}", totalKey, dbTotal);
                return true;
            }
            // Race: another caller set it concurrently. Fall through to INCRBY.
        }
        // Key existed (holds pre-adjustment value) - INCRBY delta to sync.
        return redisManager.addTotalV2(shopId, skuId, delta);
    }

    @Override
    public boolean decreaseTotal(String shopId, String skuId, long amount) {
        return redisManager.decreaseTotalV2(shopId, skuId, amount);
    }

    @Override
    public boolean increaseDeducted(String shopId, String skuId, long amount) {
        return redisManager.increaseDeductedV2(shopId, skuId, amount);
    }

    // ========================== 内部方法 ==========================

    /**
     * 确保 V2 total key 已初始化。
     * 若 Redis 中不存在，则从 DB inventory_stock 按 (shopId, skuId) 读取 totalQuantity 并 SETNX。
     * 若 DB 中也不存在，写入 0（该 SKU 视为缺货）。
     */
    private void ensureTotalKeyInitialized(String shopId, String skuId) {
        String totalKey = redisManager.getTotalKeyV2(shopId, skuId);
        Boolean exists = redisTemplate.hasKey(totalKey);
        if (Boolean.TRUE.equals(exists)) {
            return;
        }

        long totalQty = 0;
        try {
            Optional<InventoryStockEntity> stockOpt = stockRepository.findByShopIdAndSkuId(shopId, skuId);
            if (stockOpt.isPresent()) {
                totalQty = stockOpt.get().getTotalQuantity();
            } else {
                log.warn("DB inventory_stock not found for shopId={}, skuId={}, setting total=0", shopId, skuId);
            }
        } catch (Exception e) {
            log.error("Failed to load totalQuantity from DB for shopId={}, skuId={}", shopId, skuId, e);
        }

        // SETNX: 仅在 key 不存在时设置（避免并发覆盖）
        Boolean set = redisTemplate.opsForValue().setIfAbsent(totalKey, String.valueOf(totalQty));
        if (Boolean.TRUE.equals(set)) {
            log.info("Initialized Redis total key: {}={}", totalKey, totalQty);
        }
    }
}
