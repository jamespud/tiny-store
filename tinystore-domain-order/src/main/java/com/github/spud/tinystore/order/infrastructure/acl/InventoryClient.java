package com.github.spud.tinystore.order.infrastructure.acl;

import com.github.spud.tinystore.order.infrastructure.acl.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Inventory 域 RPC 客户端
 */
@FeignClient(name = "tinystore-inventory-service")
public interface InventoryClient {

    // ========================== V2 新接口 ==========================

    /**
     * V2：库存扣减（Redis 原子扣减，返回按 SKU 的 occupyPairs）
     */
    @PostMapping("/api/inventory/deduct")
    InventoryDeductResponse deduct(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody InventoryDeductRequest request);

    /**
     * V2：库存释放（按 SKU 回滚，shopId + skuId + occupyId）
     */
    @PostMapping("/api/inventory/release")
    InventoryReleaseResponseV2 releaseV2(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody InventoryReleaseRequestV2 request);

    /**
     * 库存补货接口（退款时调用）
     */
    @PostMapping("/api/inventory/restock")
    InventoryRestockResponse restock(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody InventoryRestockRequest request
    );
}
