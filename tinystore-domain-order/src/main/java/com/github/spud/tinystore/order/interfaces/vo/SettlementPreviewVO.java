package com.github.spud.tinystore.order.interfaces.vo;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author Spud
 * @date 2025/8/16
 */
@AllArgsConstructor
@Data
public class SettlementPreviewVO {

    private List<Snapshot> snapshot;
    private Summary summary;
    private final String idempotencyKey;
    private LocalDateTime expireAt;

    /**
     * @param productId 商品ID
     * @param skuId     SKU ID
     * @param title     商品标题
     * @param spec      商品规格
     * @param unitPrice 单价（分）
     * @param quantity  数量
     * @param lineTotal 小计（分）
     */
    // TODO: 优惠券
    public record Snapshot(long productId, long skuId, String title, String spec, long unitPrice,
                           int quantity, long lineTotal) {

    }


    /**
     * @param total       小计
     * @param discount    优惠总额
     * @param shippingFee 运费
     * @param tax         税费
     * @param payable     应付总额
     */
    public record Summary(long total, long discount, long shippingFee, long tax, long payable) {

    }
}
