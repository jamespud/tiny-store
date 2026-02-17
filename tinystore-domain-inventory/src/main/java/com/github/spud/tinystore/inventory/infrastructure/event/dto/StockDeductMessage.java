package com.github.spud.tinystore.inventory.infrastructure.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 库存扣减事件消息（Kafka）
 * <p>
 * 用于异步库存扣减场景，由订单域或其他上游系统发布到 Kafka topic: stock-deduct。
 * <p>
 * 消息格式：
 * <pre>
 * {
 *   "orderId": "ORDER123",
 *   "idempotencyKey": "optional-custom-key", // 可选，默认使用 orderId
 *   "items": [
 *     {"shopId": "SHOP1", "skuId": "SKU001", "quantity": 2},
 *     {"shopId": "SHOP1", "skuId": "SKU002", "quantity": 1}
 *   ]
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockDeductMessage {

    /**
     * 订单 ID（必需）
     * - 用作 Kafka partition key，保证同一订单的事件顺序消费
     * - 用作默认的幂等键
     */
    private String orderId;

    /**
     * 自定义幂等键（可选）
     * - 未提供时，Consumer 使用 orderId 作为幂等键
     * - 用于支持更细粒度的幂等控制（如：orderId + requestId）
     */
    private String idempotencyKey;

    /**
     * 扣减项列表（必需）
     * - 每个 item 包含 shopId + skuId + quantity
     * - 同一消息内的 (shopId, skuId) 不允许重复
     */
    private List<DeductItem> items;

    /**
     * 单个扣减项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DeductItem {
        /**
         * 店铺 ID
         */
        private String shopId;

        /**
         * SKU ID
         */
        private String skuId;

        /**
         * 扣减数量（必须 > 0）
         */
        private Integer quantity;
    }
}
