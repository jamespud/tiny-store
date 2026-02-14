package com.github.spud.tinystore.inventory.domain.command;

import com.github.spud.tinystore.inventory.domain.value.OccupyPair;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 库存释放命令
 */
@Data
@Builder
public class InventoryReleaseCommand {

    private String orderId;
    private String idempotencyKey;
    private String reason;
    private List<OccupyPair> occupyPairs;
}
