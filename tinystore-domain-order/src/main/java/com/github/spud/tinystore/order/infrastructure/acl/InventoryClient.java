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

    /**
     * 库存预占接口
     *
     * @param idempotencyKey 幂等键
     * @param request PreOccupy 请求
     * @return PreOccupy 响应
     */
    @PostMapping("/api/inventory/stock/pre-occupy")
    InventoryPreOccupyResponse preOccupy(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody InventoryPreOccupyRequest request
    );

    /**
     * 库存确认接口
     *
     * @param idempotencyKey 幂等键
     * @param request Commit 请求
     * @return Commit 响应
     */
    @PostMapping("/api/inventory/stock/commit")
    InventoryCommitResponse commit(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody InventoryCommitRequest request
    );

    /**
     * 库存释放接口
     *
     * @param idempotencyKey 幂等键
     * @param request Release 请求
     * @return Release 响应
     */
    @PostMapping("/api/inventory/stock/release")
    InventoryReleaseResponse release(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody InventoryReleaseRequest request
    );

    /**
     * 库存补货接口（退款时调用）
     *
     * @param idempotencyKey 幂等键
     * @param request Restock 请求
     * @return Restock 响应
     */
    @PostMapping("/api/inventory/stock/restock")
    InventoryRestockResponse restock(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody InventoryRestockRequest request
    );
}
