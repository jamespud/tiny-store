package com.github.spud.tinystore.inventory.domain.value;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 库存占用凭证（shopId + skuId + occupyId 三元组）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OccupyPair {
    private String shopId;
    private String skuId;
    private String occupyId;
}
