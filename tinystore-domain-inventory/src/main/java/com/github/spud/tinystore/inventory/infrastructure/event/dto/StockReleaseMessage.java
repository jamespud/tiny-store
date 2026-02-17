package com.github.spud.tinystore.inventory.infrastructure.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 库存释放事件消息（Kafka）
 * <p>
 * 用于异步库存释放/回滚场景，由订单域在订单取消/超时时发布到 Kafka topic: stock-release。
 * <p>
 * 消息格式：
 * <pre>
 * {
 *   "orderId": "ORDER123",
 *   "idempotencyKey": "optional-custom-key", // 可选
 *   "reason": "ORDER_CANCELLED",
 *   "occupyPairs": [
 *     {"shopId": "SHOP1", "skuId": "SKU001", "occupyId": "uuid-1"},
 *     {"shopId": "SHOP1", "skuId": "SKU002", "occupyId": "uuid-2"}
 *   ]
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockReleaseMessage {

    /**
     * 订单 ID（必需）
     * - 用作 Kafka partition key
     * - 用作默认的幂等键
     */
    private String orderId;

    /**
     * 自定义幂等键（可选）
     * - 未提供时，Consumer 使用 orderId 作为幂等键
     */
    private String idempotencyKey;

    /**
     * 释放原因（可选）
     * - 常见取值：ORDER_CANCELLED, ORDER_TIMEOUT, PAYMENT_FAILED 等
     * - 用于审计和排查
     */
    private String reason;

    /**
     * 占用凭证列表（必需）
     * - 每个 pair 包含 shopId + skuId + occupyId（扣减时返回的占用 ID）
     * - occupyId 用于精确匹配 Redis sorted set 中的 member
     */
    private List<OccupyPairDto> occupyPairs;

    /**
     * 占用凭证 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OccupyPairDto {
        /**
         * 店铺 ID
         */
        private String shopId;

        /**
         * SKU ID
         */
        private String skuId;

        /**
         * 占用 ID（扣减时生成的 UUID）
         */
        private String occupyId;
    }
}
