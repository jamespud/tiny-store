package com.github.spud.tinystore.inventory.interfaces.dto;

import lombok.Data;

@Data
public class StockRestockResponse {

	private boolean success;
	private String message;

	public static StockRestockResponse ok(String message) {
		StockRestockResponse r = new StockRestockResponse();
		r.success = true;
		r.message = message;
		return r;
	}

	public static StockRestockResponse fail(String message) {
		StockRestockResponse r = new StockRestockResponse();
		r.success = false;
		r.message = message;
		return r;
	}
}

