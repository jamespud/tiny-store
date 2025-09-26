package com.github.spud.tinystore.order.interfaces.dto.response;

import com.github.spud.tinystore.order.application.result.PreviewOrderResult.OrderSummary;
import com.github.spud.tinystore.order.application.result.PreviewOrderResult.ShopProductSnapshot;
import lombok.Data;

import java.util.List;

/**
 * @author Spud
 * @date 2025/8/16
 */
@Data
public class PreviewOrderVO {

	private List<ShopProductSnapshot> lines;
	private OrderSummary summary;
	private long expireAt; // 快照过期时间，单位秒
	private String signature; // 用于防止篡改
	private final String idempotencyKey;

	public PreviewOrderVO(List<ShopProductSnapshot> lines, OrderSummary summary, long expireAt) {
		this.lines = lines;
		this.summary = summary;
		this.expireAt = expireAt;
		this.idempotencyKey = java.util.UUID.randomUUID().toString();
	}
}
