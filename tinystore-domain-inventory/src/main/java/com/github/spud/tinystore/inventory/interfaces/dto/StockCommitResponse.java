package com.github.spud.tinystore.inventory.interfaces.dto;

import lombok.Data;

@Data
public class StockCommitResponse {

	private boolean success;
	private String message;

	public static StockCommitResponse ok(String message) {
		StockCommitResponse r = new StockCommitResponse();
		r.success = true;
		r.message = message;
		return r;
	}

	public static StockCommitResponse fail(String message) {
		StockCommitResponse r = new StockCommitResponse();
		r.success = false;
		r.message = message;
		return r;
	}
}

