package com.github.spud.tinystore.inventory.interfaces.dto;

import lombok.Data;

@Data
public class StockReleaseResponse {

	private boolean success;
	private String message;

	public static StockReleaseResponse ok(String message) {
		StockReleaseResponse r = new StockReleaseResponse();
		r.success = true;
		r.message = message;
		return r;
	}

	public static StockReleaseResponse fail(String message) {
		StockReleaseResponse r = new StockReleaseResponse();
		r.success = false;
		r.message = message;
		return r;
	}
}

