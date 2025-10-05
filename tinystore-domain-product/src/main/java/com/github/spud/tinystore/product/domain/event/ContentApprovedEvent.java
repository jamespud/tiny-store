package com.github.spud.tinystore.product.domain.event;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 内容审核通过事件 当商品详情审核通过时触发
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentApprovedEvent {

	private String productId;
	private Integer version;
	private Instant occurredAt;

	public ContentApprovedEvent(String productId, Integer version) {
		this.productId = productId;
		this.version = version;
		this.occurredAt = Instant.now();
	}
}
