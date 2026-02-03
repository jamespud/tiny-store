package com.github.spud.tinystore.order.infrastructure.acl;

import com.github.spud.tinystore.order.infrastructure.acl.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Promotion 域 RPC 客户端
 */
@FeignClient(name = "promotion-service")
public interface PromotionClient {

    /**
     * 优惠 Quote 接口
     *
     * @param idempotencyKey 幂等键
     * @param request Quote 请求
     * @return Quote 响应
     */
    @PostMapping("/api/promotion/checkout/quote")
    PromotionQuoteResponse quote(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody PromotionQuoteRequest request
    );

    /**
     * 优惠 Commit 接口
     *
     * @param idempotencyKey 幂等键
     * @param request Commit 请求
     * @return Commit 响应
     */
    @PostMapping("/api/promotion/checkout/commit")
    PromotionCommitResponse commit(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody PromotionCommitRequest request
    );

    /**
     * 优惠 Release 接口
     *
     * @param idempotencyKey 幂等键
     * @param request Release 请求
     * @return Release 响应
     */
    @PostMapping("/api/promotion/checkout/release")
    PromotionReleaseResponse release(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody PromotionReleaseRequest request
    );
}
