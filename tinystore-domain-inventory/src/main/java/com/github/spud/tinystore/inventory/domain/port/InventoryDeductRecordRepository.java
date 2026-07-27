package com.github.spud.tinystore.inventory.domain.port;

import com.github.spud.tinystore.inventory.domain.value.OccupyPair;

import java.util.List;

/**
 * 扣减流水/占用记录仓储（领域端口）
 * <p>
 * 用于 Redis 扣减成功后在 DB 本地事务中落流水，保障最小一致性。
 */
public interface InventoryDeductRecordRepository {

    /**
     * 保存扣减记录（一个 orderId 对应多条 SKU 占用）
     *
     * @param orderId        订单 ID
     * @param idempotencyKey 幂等键
     * @param occupyPairs    占用凭证列表
     * @param quantities     与 occupyPairs 一一对应的扣减数量
     */
    void saveDeducted(String orderId, String idempotencyKey,
                      List<OccupyPair> occupyPairs, List<Integer> quantities);

    /**
     * 标记已释放（按 occupyPairs 逐条更新状态）
     *
     * @param orderId     订单 ID
     * @param occupyPairs 需释放的占用凭证
     * @param reason      释放原因
     */
    void markReleased(String orderId, List<OccupyPair> occupyPairs, String reason);

    /**
     * 持久化 Redis 补偿回滚失败的审计记录（canonical 预扣路径专用）。
     * <p>
     * 依据 failure-arbitration.md §2 原则 3：所有 Redis 补偿失败必须写执行日志。
     * DB 终态（RELEASED / EXPIRED）已提交，此记录仅用于运维审计与人工补偿。
     *
     * @param reservationId 预扣凭证 ID（唯一标识此次失败事件）
     * @param shopId        店铺 ID
     * @param skuId         SKU ID
     * @param reason        失败原因描述（含错误类型）
     */
    void saveRedisRollbackFailed(String reservationId, String shopId, String skuId, String reason);

    /**
     * Persist an execution-log entry when Redis addTotal fails after a committed
     * DB adjustment (adjustment has no reservationId, hence a dedicated method).
     * Per failure-arbitration.md §2 principle 3: Redis compensation failures must
     * write an execution log. DB state is already terminal; this is for ops audit.
     */
    void saveRedisAdjustFailed(String shopId, String skuId, long delta, String reason);
}
