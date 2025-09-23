package com.github.spud.tinystore.order.interfaces.dto.response;

import com.github.spud.tinystore.order.application.result.PreviewOrderResult;
import com.github.spud.tinystore.order.application.result.PreviewOrderResult.ShopProductSnapshot;

import java.util.List;

/**
 * @author Spud
 * @date 2025/9/4
 */
public class CreateOrderVO {

    private List<ShopProductSnapshot> lines;
    private PreviewOrderResult.OrderSummary summary;
    private String idempotencyKey;
}
