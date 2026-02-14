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
}
