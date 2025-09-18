package com.github.spud.tinystore.order.interfaces.dto;

import java.util.List;

/**
 * @author Spud
 * @date 2025/9/18
 */
public record ShopProductDto(String shopId, List<ProductDto> products) {

}