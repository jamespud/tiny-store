package com.github.spud.tinystore.inventory.interfaces.dto;

import java.util.Collections;
import java.util.List;
import lombok.Data;

@Data
public class StockPreOccupyResponse {

	private boolean success;
	private List<String> preOccupyIds = Collections.emptyList();
	private List<String> lackSkuIds = Collections.emptyList();
	private long expiresAtEpochMs;
	private String message;

	public static StockPreOccupyResponse ok(List<String> preOccupyIds, long expiresAtEpochMs) {
		StockPreOccupyResponse r = new StockPreOccupyResponse();
		r.success = true;
		r.preOccupyIds = preOccupyIds == null ? Collections.emptyList() : preOccupyIds;
		r.expiresAtEpochMs = expiresAtEpochMs;
		r.message = "ok";
		return r;
	}

	public static StockPreOccupyResponse fail(List<String> lackSkuIds, String message) {
		StockPreOccupyResponse r = new StockPreOccupyResponse();
		r.success = false;
		r.lackSkuIds = lackSkuIds == null ? Collections.emptyList() : lackSkuIds;
		r.message = message;
		return r;
	}
}

