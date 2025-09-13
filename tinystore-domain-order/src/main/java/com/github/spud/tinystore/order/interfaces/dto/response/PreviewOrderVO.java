package com.github.spud.tinystore.order.interfaces.dto.response;

import com.github.spud.tinystore.order.application.result.PreviewOrderResult.ShopProductSnapshot;
import com.github.spud.tinystore.order.interfaces.dto.OrderSummary;

import java.util.List;
import lombok.Data;

/**
 * @author Spud
 * @date 2025/8/16
 */
@Data
public class PreviewOrderVO {

	private List<ShopProductSnapshot> lines;
	private OrderSummary summary;
	private final String idempotencyKey;
	private long expireAt; // 快照过期时间，单位秒
	private String signature; // 用于防止篡改
}
