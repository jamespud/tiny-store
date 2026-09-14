package com.github.spud.tinystore.inventory.infrastructure.scheduler;

import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryReservationRepository;
import com.github.spud.tinystore.inventory.infrastructure.util.InventoryRedisManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * V2 库存 uncommit 超时清理定时任务
 * <p>
 * 职责：
 * - 通过 Redis SCAN 遍历所有 inventory:uncommit:{shopId}:{skuId} key
 * - 对每个 key 做两段清理：
 *   1. <b>孤儿定向回收</b>（C12 safety net）：member age &gt; orphanCheckDelay 且 DB 中不存在对应
 *      reservation_id 的，才回收。它把「下单事务回滚 / JVM 崩溃导致预扣已记账但预约永远不建立」的
 *      恢复时间从兜底的 timeout（默认 30 分钟）缩短到 orphanCheckDelay。
 *   2. <b>兜底超时清理</b>：沿用 cleanTimeoutUncommitV2，按时间阈值批量回收。
 * - 定时触发（每分钟一次）或可手动触发（测试用）
 *
 * <p><b>正确性边界（勿再简化为"先查 DB 就一定安全"）</b>：本任务先查 DB 再回收，两次调用之间
 * {@code INVENTORY_RESERVE_DB} 消费端理论上可以插入预约行 —— 单纯"先查 DB 再 Lua"无法提供
 * "绝不误伤合法预约"的保证。真正让这条链成立的是两个约束的组合：
 * <ol>
 *   <li>消费端拒绝 {@code expireAt <= now} 的迟到事件（{@code OrderEventConsumer}）：预约窗口一关，
 *       任何迟到事件都不会再建立 DB 行；</li>
 *   <li>这里强制 {@code orphanCheckDelay > maxReservationTtl}（构造时校验，违反即启动失败）：等到成员
 *       够老时，所有合法预约窗口都已关闭，DB 不会再"事后"出现行。</li>
 * </ol>
 * 二者缺一，本任务就可能释放一个仍然合法的预扣（方向是超卖而非少卖）。
 */
@Slf4j
@Component
public class InventoryUncommitV2CleanupTask {

    private final StringRedisTemplate redisTemplate;
    private final InventoryRedisManager inventoryRedisManager;
    private final JpaInventoryReservationRepository reservationRepository;

    /**
     * 未提交预扣的回收超时。
     *
     * <p>配置不变量：<b>该值必须大于合法的最大预约 TTL</b>（订单侧 reservationTtlMinutes），
     * 否则会把仍然合法的预扣提前释放。默认 30 分钟。
     */
    @org.springframework.beans.factory.annotation.Value("${inventory.uncommit.timeout:PT30M}")
    private java.time.Duration uncommitTimeout;

    /**
     * 孤儿定向回收的年龄阈值。
     *
     * <p>配置不变量（启动时强制校验）：<b>必须大于 {@link #maxReservationTtl}</b>。等到成员够老时
     * 所有合法预约窗口都已关闭，而消费端又拒绝过期事件，因此 DB 不会再事后出现行；同时必须小于
     * 兜底 {@link #uncommitTimeout} 才有加速意义。默认 20 分钟（> 默认 TTL 15 分钟，< 兜底 30 分钟）。
     */
    @org.springframework.beans.factory.annotation.Value("${inventory.uncommit.orphan-check-delay:PT20M}")
    private java.time.Duration orphanCheckDelay;

    /**
     * 调用方可能申请的最大合法预约 TTL（订单侧 reservationTtlMinutes 的上界）。
     *
     * <p>只用于启动时校验 {@link #orphanCheckDelay} 的关系；真实 TTL 由每个事件的 expireAt 携带。
     */
    @org.springframework.beans.factory.annotation.Value("${inventory.uncommit.max-reservation-ttl:PT15M}")
    private java.time.Duration maxReservationTtl;

    /** 单次 DB 存在性查询的最大 IN 大小，避免超长 SQL。 */
    private static final int DB_CHECK_BATCH_SIZE = 500;

    private static boolean isStrictlyPositive(java.time.Duration value) {
        return !value.isZero() && !value.isNegative();
    }

    public InventoryUncommitV2CleanupTask(StringRedisTemplate redisTemplate,
                                          InventoryRedisManager inventoryRedisManager,
                                          JpaInventoryReservationRepository reservationRepository) {
        this.redisTemplate = redisTemplate;
        this.inventoryRedisManager = inventoryRedisManager;
        this.reservationRepository = reservationRepository;
    }

    /**
     * 配置不变量的启动校验（P0：这条链的正确性依赖它，不能只是注释里的建议）。
     */
    @jakarta.annotation.PostConstruct
    void validateConfigInvariants() {
        if (!isStrictlyPositive(orphanCheckDelay.minus(maxReservationTtl))) {
            throw new IllegalStateException(String.format(
                "inventory.uncommit.orphan-check-delay=%s must be greater than "
                    + "inventory.uncommit.max-reservation-ttl=%s: otherwise the orphan reclaim can run while a "
                    + "legitimate reservation window is still open and release a pre-deduction that is about "
                    + "to have its DB row created (oversell direction)",
                orphanCheckDelay, maxReservationTtl));
        }
        if (!isStrictlyPositive(uncommitTimeout.minus(orphanCheckDelay))) {
            throw new IllegalStateException(String.format(
                "inventory.uncommit.timeout=%s must be greater than inventory.uncommit.orphan-check-delay=%s: "
                    + "the orphan sweep is only meaningful as an accelerator in front of the timeout fallback",
                uncommitTimeout, orphanCheckDelay));
        }
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
        int reclaimedCount = 0;
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

                    // 第一段：孤儿定向回收（先查 DB 权威状态，绝不误伤合法预约）
                    reclaimedCount += reclaimOrphans(shopId, skuId);

                    // 第二段：兜底超时清理
                    long cleaned = inventoryRedisManager.cleanTimeoutUncommitV2(shopId, skuId,
                            uncommitTimeout.toMillis());
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

        log.info("V2 uncommit cleanup task finished: scanned={}, orphansReclaimed={}, cleaned={}, errors={}",
                 scannedCount, reclaimedCount, cleanedCount, errorCount);
    }

    /**
     * 回收某个 (shopId, skuId) 下的孤儿预扣 member：年龄超过 {@link #orphanCheckDelay}
     * 且 DB 中不存在对应 reservation_id 的 member。
     *
     * @return 实际回收的 member 数量
     */
    private int reclaimOrphans(String shopId, String skuId) {
        List<String> candidates = inventoryRedisManager.findUncommitMembersOlderThan(
                shopId, skuId, orphanCheckDelay.toMillis());
        if (candidates.isEmpty()) {
            return 0;
        }

        Set<String> existing = new HashSet<>();
        for (int i = 0; i < candidates.size(); i += DB_CHECK_BATCH_SIZE) {
            List<String> batch = candidates.subList(i, Math.min(i + DB_CHECK_BATCH_SIZE, candidates.size()));
            existing.addAll(reservationRepository.findExistingReservationIds(batch));
        }

        List<String> orphans = new ArrayList<>();
        for (String member : candidates) {
            if (!existing.contains(member)) {
                orphans.add(member);
            }
        }
        if (orphans.isEmpty()) {
            // 所有候选都有 DB 权威预约 —— 合法预扣，绝不释放
            log.debug("Orphan scan: all {} candidates still have DB reservations, shopId={}, skuId={}",
                    candidates.size(), shopId, skuId);
            return 0;
        }

        long reclaimed = inventoryRedisManager.reclaimOrphanMembersV2(shopId, skuId, orphans);
        if (reclaimed > 0) {
            log.warn("Reclaimed {} orphan pre-deductions (candidates={}, legitimate={}) for shopId={}, skuId={}",
                    reclaimed, candidates.size(), existing.size(), shopId, skuId);
        }
        return (int) reclaimed;
    }
}
