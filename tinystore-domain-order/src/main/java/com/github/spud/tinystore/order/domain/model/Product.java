package com.github.spud.tinystore.order.domain.model;

/**
 * 商品快照
 *
 * @param shopId    店铺ID
 * @param skuId     商品SKU ID
 * @param title     商品标题
 * @param specJson  商品规格JSON
 * @param unitPrice 商品单价
 */
public record Product(String shopId, String skuId, String title, String specJson,
                      Money unitPrice) {

}
