package com.github.spud.tinystore.order.domain.model.vo;

/**
 * @author Spud
 * @date 2025/10/18
 */
public record OrderNo(String value) {
	
	public static OrderNo of(String value) {
		return new OrderNo(value);
	}

}
