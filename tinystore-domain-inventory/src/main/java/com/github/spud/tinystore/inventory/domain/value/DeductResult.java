package com.github.spud.tinystore.inventory.domain.value;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 扣减结果值对象（用于幂等缓存回放）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeductResult {

    private boolean success;
    private String message;
    private List<OccupyPair> occupyPairs;
    private List<String> lackSkuIds;
}
