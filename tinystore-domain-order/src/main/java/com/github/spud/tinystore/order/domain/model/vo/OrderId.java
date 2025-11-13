package com.github.spud.tinystore.order.domain.model.vo;

public record OrderId(String value) {
	
	public static OrderId of(String value) {
		return new OrderId(value);
	}

}
