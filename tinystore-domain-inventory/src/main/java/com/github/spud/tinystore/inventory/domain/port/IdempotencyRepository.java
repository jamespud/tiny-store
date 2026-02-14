package com.github.spud.tinystore.inventory.domain.port;

import com.github.spud.tinystore.inventory.domain.value.DeductResult;

import java.util.Optional;

/**
 * 幂等仓储（领域端口）
 * <p>
 * 用于缓存扣减 / 释放结果，支持幂等重放。
 */
public interface IdempotencyRepository {

    /**
     * 查询已缓存的扣减结果
     */
    Optional<DeductResult> getDeductResult(String idempotencyKey);

    /**
     * 缓存扣减结果
     */
    void saveDeductResult(String idempotencyKey, DeductResult result);

    /**
     * 幂等键是否已存在（通用，检查 deduct 或 release）
     */
    boolean exists(String idempotencyKey);

    /**
     * 检查 release 是否已完成（仅检查 release 幂等键）
     */
    boolean isReleased(String idempotencyKey);

    /**
     * 标记 release 已完成
     */
    void markReleased(String idempotencyKey);
}
