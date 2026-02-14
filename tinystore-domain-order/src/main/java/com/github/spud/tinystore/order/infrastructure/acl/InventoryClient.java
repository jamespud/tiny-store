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

    // ========================== 旧接口（已过时） ==========================

    /**
     * @deprecated 使用 {@link #deduct} 替代
     */
    @Deprecated
    @PostMapping("/api/inventory/stock/reserve")
    InventoryPreOccupyResponse reserve(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody InventoryPreOccupyRequest request);

    /**
     * @deprecated 使用 {@link #deduct} 替代
     */
    @Deprecated
    @PostMapping("/api/inventory/stock/pre-occupy")
    InventoryPreOccupyResponse preOccupy(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody InventoryPreOccupyRequest request
    );

    /**
     * @deprecated 不再需要两步提交
     */
    @Deprecated
    @PostMapping("/api/inventory/stock/commit")
    InventoryCommitResponse commit(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody InventoryCommitRequest request
    );

    /**
     * @deprecated 使用 {@link #releaseV2} 替代
     */
    @Deprecated
    @PostMapping("/api/inventory/stock/release")
    InventoryReleaseResponse release(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody InventoryReleaseRequest request
    );

    /**
     * 库存补货接口（退款时调用）
     *
     * @deprecated 仍用于退货退款场景，未来可考虑与新链路合并
     */
    @Deprecated
    @PostMapping("/api/inventory/stock/restock")
    InventoryRestockResponse restock(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody InventoryRestockRequest request
    );
}
