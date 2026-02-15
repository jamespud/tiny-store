package com.github.spud.tinystore.inventory.domain.service;

import com.github.spud.tinystore.inventory.domain.command.InventoryDeductCommand;
import com.github.spud.tinystore.inventory.domain.command.InventoryReleaseCommand;
import com.github.spud.tinystore.inventory.domain.port.IdempotencyRepository;
import com.github.spud.tinystore.inventory.domain.port.InventoryDeductGateway;
import com.github.spud.tinystore.inventory.domain.port.InventoryDeductRecordRepository;
import com.github.spud.tinystore.inventory.domain.value.DeductResult;
import com.github.spud.tinystore.inventory.domain.value.OccupyPair;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 库存扣减领域服务
 * <p>
 * 职责：
 * - 幂等校验与回放
 * - 原子一致性语义（全成功/全失败，失败回滚已扣）
 * - 最小一致性保障（Redis 扣减 + DB 流水本地事务）
 * <p>
 * 只依赖领域端口（port），不感知 Redis / JPA / 具体 Key 设计。
 */
@Slf4j
@Service
public class InventoryDeductDomainService {

    private final InventoryDeductGateway deductGateway;
    private final InventoryDeductRecordRepository recordRepository;
    private final IdempotencyRepository idempotencyRepository;

    public InventoryDeductDomainService(InventoryDeductGateway deductGateway,
                                        InventoryDeductRecordRepository recordRepository,
                                        IdempotencyRepository idempotencyRepository) {
        this.deductGateway = deductGateway;
        this.recordRepository = recordRepository;
        this.idempotencyRepository = idempotencyRepository;
    }

    // ========================== 扣减 ==========================

    /**
     * 执行库存扣减
     * <ol>
     *   <li>orderId绑定检测：同一幂等键必须绑定同一orderId，否则返回冲突</li>
     *   <li>幂等回放：如已执行过则返回缓存结果</li>
     *   <li>校验：items 中 (shopId, skuId) 不允许重复</li>
     *   <li>逐条预扣，任一失败 → 对已成功项全部回滚 → 返回失败</li>
     *   <li>全部成功 → DB 落扣减流水（本地事务） → 若 DB 失败则补偿回滚 Redis</li>
     *   <li>缓存幂等结果 → 返回成功</li>
     * </ol>
     */
    public DeductResult deduct(InventoryDeductCommand command) {
        // 1. orderId 冲突检测（E-07）
        Optional<String> boundOrderId = idempotencyRepository.getDeductOrderId(command.getIdempotencyKey());
        if (boundOrderId.isPresent()) {
            if (!boundOrderId.get().equals(command.getOrderId())) {
                log.warn("Idempotency conflict: key={}, bound={}, current={}", 
                         command.getIdempotencyKey(), boundOrderId.get(), command.getOrderId());
                return DeductResult.builder()
                        .success(false)
                        .message("IDEMPOTENCY_CONFLICT")
                        .occupyPairs(List.of())
                        .lackSkuIds(List.of())
                        .build();
            }
        } else {
            // 尝试绑定 orderId（SETNX 语义）
            boolean bound = idempotencyRepository.bindDeductOrderIdIfAbsent(
                    command.getIdempotencyKey(), command.getOrderId());
            if (!bound) {
                // 并发竞态：绑定失败，再次读取并比对
                Optional<String> reread = idempotencyRepository.getDeductOrderId(command.getIdempotencyKey());
                if (reread.isPresent() && !reread.get().equals(command.getOrderId())) {
                    log.warn("Idempotency conflict (race): key={}, bound={}, current={}", 
                             command.getIdempotencyKey(), reread.get(), command.getOrderId());
                    return DeductResult.builder()
                            .success(false)
                            .message("IDEMPOTENCY_CONFLICT")
                            .occupyPairs(List.of())
                            .lackSkuIds(List.of())
                            .build();
                }
            }
        }

        // 2. 幂等回放
        Optional<DeductResult> cached = idempotencyRepository.getDeductResult(command.getIdempotencyKey());
        if (cached.isPresent()) {
            log.info("Deduct idempotent hit: idempotencyKey={}", command.getIdempotencyKey());
            return cached.get();
        }

        // 3. 重复 sku 校验
        Set<String> seen = new HashSet<>();
        for (InventoryDeductCommand.Item item : command.getItems()) {
            String key = item.getShopId() + ":" + item.getSkuId();
            if (!seen.add(key)) {
                return DeductResult.builder()
                        .success(false)
                        .message("DUPLICATE_SKU_ID")
                        .occupyPairs(List.of())
                        .lackSkuIds(List.of(item.getSkuId()))
                        .build();
            }
        }

        // 4. 逐条预扣（原子一致性：失败则回滚所有已成功项）
        List<OccupyPair> successPairs = new ArrayList<>();
        List<Integer> successQuantities = new ArrayList<>();

        for (InventoryDeductCommand.Item item : command.getItems()) {
            Optional<String> occupyId = deductGateway.preDeduct(
                    item.getShopId(), item.getSkuId(), item.getQuantity(), command.getOrderId());

            if (occupyId.isEmpty()) {
                // 回滚已成功项
                rollbackAll(successPairs);
                return DeductResult.builder()
                        .success(false)
                        .message("STOCK_LACK")
                        .lackSkuIds(List.of(item.getSkuId()))
                        .build();
            }
            successPairs.add(OccupyPair.builder()
                    .shopId(item.getShopId())
                    .skuId(item.getSkuId())
                    .occupyId(occupyId.get())
                    .build());
            successQuantities.add(item.getQuantity());
        }

        // 5. DB 落流水（最小一致性保障）
        try {
            recordRepository.saveDeducted(
                    command.getOrderId(), command.getIdempotencyKey(),
                    successPairs, successQuantities);
        } catch (Exception e) {
            log.error("DB deduct record failed, compensating Redis rollback: orderId={}", command.getOrderId(), e);
            rollbackAll(successPairs);
            return DeductResult.builder()
                    .success(false)
                    .message("DB_RECORD_FAILED")
                    .build();
        }

        // 6. 缓存幂等结果
        DeductResult result = DeductResult.builder()
                .success(true)
                .message("ok")
                .occupyPairs(successPairs)
                .build();
        try {
            idempotencyRepository.saveDeductResult(command.getIdempotencyKey(), result);
        } catch (Exception e) {
            log.warn("Failed to cache deduct idempotency result: key={}", command.getIdempotencyKey(), e);
            // 不阻断返回
        }

        return result;
    }

    // ========================== 释放 ==========================

    /**
     * 执行库存释放（按 SKU 回滚）
     * <ol>
     *   <li>幂等命中 → 直接返回成功</li>
     *   <li>逐条回滚 Redis（member 不存在视为已回滚，整体仍 success）</li>
     *   <li>尽力写 DB release 流水（失败仅 error 日志，不阻断返回）</li>
     *   <li>标记幂等完成</li>
     * </ol>
     */
    public DeductResult release(InventoryReleaseCommand command) {
        // 1. 幂等（仅检查 release 专用键，防止被 deduct 幂等短路）
        if (idempotencyRepository.isReleased(command.getIdempotencyKey())) {
            log.info("Release idempotent hit: idempotencyKey={}", command.getIdempotencyKey());
            return DeductResult.builder().success(true).message("ok").build();
        }

        // 2. 逐条回滚
        for (OccupyPair pair : command.getOccupyPairs()) {
            boolean ok = deductGateway.rollback(pair.getShopId(), pair.getSkuId(), pair.getOccupyId());
            if (!ok) {
                // rollback 返回 false 在当前实现里可能是"member 不存在"（已回滚），
                // 也可能是 Redis 异常。这里记录但不阻断，保持回滚优先语义。
                log.warn("Rollback returned false (may be already rolled back): shopId={}, skuId={}, occupyId={}",
                        pair.getShopId(), pair.getSkuId(), pair.getOccupyId());
            }
        }

        // 3. DB 流水（尽力而为）
        try {
            recordRepository.markReleased(command.getOrderId(), command.getOccupyPairs(), command.getReason());
        } catch (Exception e) {
            log.error("DB release record failed (non-blocking): orderId={}", command.getOrderId(), e);
        }

        // 4. 标记幂等
        try {
            idempotencyRepository.markReleased(command.getIdempotencyKey());
        } catch (Exception e) {
            log.warn("Failed to mark release idempotency: key={}", command.getIdempotencyKey(), e);
        }

        return DeductResult.builder().success(true).message("ok").build();
    }

    // ========================== 内部方法 ==========================

    private void rollbackAll(List<OccupyPair> pairs) {
        for (OccupyPair pair : pairs) {
            try {
                deductGateway.rollback(pair.getShopId(), pair.getSkuId(), pair.getOccupyId());
            } catch (Exception e) {
                log.error("Compensating rollback failed: shopId={}, skuId={}, occupyId={}",
                        pair.getShopId(), pair.getSkuId(), pair.getOccupyId(), e);
            }
        }
    }
}
