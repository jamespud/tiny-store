package com.github.spud.tinystore.order.interfaces.dto;

/**
 * Product DTO for external interfaces
 *
 * @author Spud
 * @date 2025/9/19
 */
public record Product(String shopId, String skuId, String title, String specJson,
											long unitPriceCents,
											String currency) {

}