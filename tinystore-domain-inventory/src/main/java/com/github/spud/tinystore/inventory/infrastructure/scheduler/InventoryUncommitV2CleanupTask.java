package com.github.spud.tinystore.inventory.infrastructure.scheduler;

import com.github.spud.tinystore.inventory.infrastructure.util.InventoryRedisManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * V2 库存 uncommit 超时清理定时任务
 * <p>
 * 职责：
 * - 通过 Redis SCAN 遍历所有 inventory:uncommit:{shopId}:{skuId} key
 * - 解析出 (shopId, skuId) 并调用 cleanTimeoutUncommitV2
 * - 定时触发（每分钟一次）或可手动触发（测试用）
 */
@Slf4j
@Component
public class InventoryUncommitV2CleanupTask {

    private final StringRedisTemplate redisTemplate;
    private final InventoryRedisManager inventoryRedisManager;

    public InventoryUncommitV2CleanupTask(StringRedisTemplate redisTemplate,
                                          InventoryRedisManager inventoryRedisManager) {
        this.redisTemplate = redisTemplate;
        this.inventoryRedisManager = inventoryRedisManager;
    }

    /**
     * 定时触发（每 1 分钟执行一次）
     */
    @Scheduled(fixedDelayString = "PT1M")
    public void scheduledCleanup() {
        cleanupOnce();
    }

    /**
     * 执行一次完整扫描与清理（可被测试直接调用）
     */
    public void cleanupOnce() {
        log.info("V2 uncommit cleanup task started");
        int scannedCount = 0;
        int cleanedCount = 0;
        int errorCount = 0;

        ScanOptions options = ScanOptions.scanOptions()
                .match("inventory:uncommit:*:*")
                .count(100)
                .build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                scannedCount++;

                try {
                    // 解析 key: inventory:uncommit:{shopId}:{skuId}
                    String[] parts = key.split(":");
                    if (parts.length != 4) {
                        log.warn("Invalid uncommit key format, skipping: {}", key);
                        errorCount++;
                        continue;
                    }

                    String shopId = parts[2];
                    String skuId = parts[3];

                    long cleaned = inventoryRedisManager.cleanTimeoutUncommitV2(shopId, skuId, null);
                    if (cleaned > 0) {
                        cleanedCount++;
                        log.info("Cleaned {} uncommit members for shopId={}, skuId={}", cleaned, shopId, skuId);
                    }
                } catch (Exception e) {
                    log.error("Failed to clean uncommit for key: {}", key, e);
                    errorCount++;
                }
            }
        } catch (Exception e) {
            log.error("V2 uncommit cleanup task failed", e);
        }

        log.info("V2 uncommit cleanup task finished: scanned={}, cleaned={}, errors={}", 
                 scannedCount, cleanedCount, errorCount);
    }
}
