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
}
