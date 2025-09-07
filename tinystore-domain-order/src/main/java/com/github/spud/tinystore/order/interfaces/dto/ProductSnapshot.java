package com.github.spud.tinystore.order.interfaces.dto;

/**
 * @author Spud
 * @date 2025/9/4
 */
public record ProductSnapshot(ShopSnapshot shop, String spuId, String title, String skuId,
                              String skuTitle, String imageUrl, PriceSnapshot price) {

}