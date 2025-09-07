package com.github.spud.tinystore.order.domain.model;

/**
 * 子订单
 *
 * @param lineId    子订单ID
 * @param skuId     SKU ID
 * @param quantity  数目
 * @param unitPrice 单价
 */
public record OrderLine(String lineId, String skuId, String spudId, String title, String specJson,
                        Integer quantity, Money unitPrice, long lineTotalRaw,
                        long allocateDiscount, Money lineTotalNet, String snapshotHash) {

}