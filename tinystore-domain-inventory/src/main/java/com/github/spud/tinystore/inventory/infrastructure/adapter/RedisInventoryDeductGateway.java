package com.github.spud.tinystore.inventory.infrastructure.adapter;

import com.github.spud.tinystore.inventory.domain.port.InventoryDeductGateway;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryStockEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryReservationRepository;
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
    private final JpaInventoryReservationRepository reservationRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public RedisInventoryDeductGateway(InventoryRedisManager redisManager,
                                       JpaInventoryStockRepository stockRepository,
                                       JpaInventoryReservationRepository reservationRepository,
                                       RedisTemplate<String, Object> redisTemplate) {
        this.redisManager = redisManager;
        this.stockRepository = stockRepository;
        this.reservationRepository = reservationRepository;
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
            // Key absent: initialize to authoritative Redis total (dbTotal + confirmed, already post-adjustment).
            // Do NOT then INCRBY - that would double-count the delta.
            long authoritativeTotal = authoritativeRedisTotal(shopId, skuId);
            Boolean set = redisTemplate.opsForValue().setIfAbsent(totalKey, String.valueOf(authoritativeTotal));
            if (Boolean.TRUE.equals(set)) {
                String versionKey = redisManager.getVersionKeyV2(shopId, skuId);
                redisTemplate.opsForValue().setIfAbsent(versionKey, "0");
                log.info("Initialized Redis total key on adjust: {}={}", totalKey, authoritativeTotal);
                return true;
            }
            // Race: another caller set it concurrently. Fall through to INCRBY.
        }
        // Key existed (holds pre-adjustment value) - INCRBY delta to sync.
        return redisManager.addTotalV2(shopId, skuId, delta);
    }

    @Override
    public boolean decreaseTotal(String shopId, String skuId, long amount, long expectVersion) {
        return redisManager.decreaseTotalV2(shopId, skuId, amount, expectVersion);
    }

    @Override
    public boolean increaseDeducted(String shopId, String skuId, long amount, long expectVersion) {
        return redisManager.increaseDeductedV2(shopId, skuId, amount, expectVersion);
    }

    @Override
    public boolean initState(String shopId, String skuId, long targetTotal, long targetDeducted) {
        return redisManager.initStateV2(shopId, skuId, targetTotal, targetDeducted);
    }

    // ========================== 内部方法 ==========================


    /**
     * Authoritative Redis total for a fresh key: DB remaining total + CONFIRMED quantity.
     * CONFIRMED reservations consumed stock (deductConfirmed decrements total_quantity) but
     * never decremented Redis, so a re-initialized Redis total must include them
     * (matches ReconciliationSnapshot.getTargetTotal()).
     */
    private long authoritativeRedisTotal(String shopId, String skuId) {
        long confirmed = 0;
        try {
            confirmed = reservationRepository.sumQuantityByShopSkuStatus(shopId, skuId, "CONFIRMED");
        } catch (Exception e) {
            log.error("Failed to load CONFIRMED quantity for authoritative total: shopId={}, skuId={}",
                    shopId, skuId, e);
        }
        long dbTotal = 0;
        try {
            Optional<InventoryStockEntity> stockOpt = stockRepository.findByShopIdAndSkuId(shopId, skuId);
            if (stockOpt.isPresent()) {
                dbTotal = stockOpt.get().getTotalQuantity();
            } else {
                log.warn("DB inventory_stock not found for shopId={}, skuId={}, setting total=0", shopId, skuId);
            }
        } catch (Exception e) {
            log.error("Failed to load totalQuantity from DB for shopId={}, skuId={}", shopId, skuId, e);
        }
        return dbTotal + confirmed;
    }

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

        long authoritativeTotal = authoritativeRedisTotal(shopId, skuId);

        // SETNX: 仅在 key 不存在时设置（避免并发覆盖）
        Boolean set = redisTemplate.opsForValue().setIfAbsent(totalKey, String.valueOf(authoritativeTotal));
        if (Boolean.TRUE.equals(set)) {
            // version 与 total 同生（SETNX 0，不覆盖已存在的 version）
            String versionKey = redisManager.getVersionKeyV2(shopId, skuId);
            redisTemplate.opsForValue().setIfAbsent(versionKey, "0");
            log.info("Initialized Redis total key: {}={}", totalKey, authoritativeTotal);
        }
    }
}
