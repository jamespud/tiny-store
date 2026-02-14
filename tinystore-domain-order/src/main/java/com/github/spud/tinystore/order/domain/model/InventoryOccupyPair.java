package com.github.spud.tinystore.order.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 库存占用凭证值对象（shopId + skuId + occupyId 三元组）
 * <p>
 * 用于新 Redis 扣减链路，存储于 ShopOrder 中。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryOccupyPair {
    private String shopId;
    private String skuId;
    private String occupyId;
}
