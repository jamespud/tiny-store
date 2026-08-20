package com.github.spud.tinystore.inventory.domain.port;

import java.util.Optional;

/**
 * 库存扣减基础设施网关（领域端口）
 * <p>
 * 领域层只依赖此接口，不关心底层是 Redis / DB / 消息队列。
 */
public interface InventoryDeductGateway {

    /**
     * 对指定 SKU 进行预扣（原子操作）
     *
     * @param shopId  店铺ID
     * @param skuId   SKU ID
     * @param qty     扣减数量（正数）
     * @param orderId 业务ID（orderId），用于幂等与审计
     * @return 成功返回 occupyId（Redis ZSet member）；库存不足或失败返回 empty
     */
    Optional<String> preDeduct(String shopId, String skuId, int qty, String orderId);

    /**
     * 回滚已预扣的库存（原子操作）
     *
     * @param shopId   店铺ID
     * @param skuId    SKU ID
     * @param occupyId 预扣时返回的 occupyId
     * @return true=回滚成功或已回滚，false=回滚异常
     */
    boolean rollback(String shopId, String skuId, String occupyId);

    /**
     * Bump the Redis total cache by a signed delta (INCRBY), after ensuring the key
     * is initialized from DB. Best-effort, called AFTER DB commit.
     *
     * @return true if Redis INCRBY succeeded, false on Redis failure
     */
    boolean addTotal(String shopId, String skuId, long delta);

    /**
     * Atomically (re)initialize missing V2 keys from DB-authoritative targets (reconcile).
     * SETNX semantics — never overwrites an existing key. version key initialized to 0 when absent.
     *
     * @param targetTotal     authoritative Redis total = dbTotal + confirmed
     * @param targetDeducted  authoritative Redis deducted = dbPreDeducted + confirmed
     * @return true if at least one key was newly created (an action was taken)
     */
    boolean initState(String shopId, String skuId, long targetTotal, long targetDeducted);

    /**
     * Atomic dual-field oversell repair (reconcile, conservative direction).
     * One version-CAS applies BOTH totalDelta (<=0, lower total) and deductedDelta (>=0, raise
     * deducted) in a single Lua script — no partial repair, idempotent, multi-instance safe.
     *
     * @param totalDelta    &lt;=0: totalTooHigh excess to remove, else 0
     * @param deductedDelta &gt;=0: deductedTooLow deficit to add, else 0
     * @return true if applied (version matched), false on CAS skip or Redis failure
     */
    boolean repairOversell(String shopId, String skuId, long totalDelta, long deductedDelta, long expectVersion);
}
