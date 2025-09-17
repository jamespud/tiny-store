package com.github.spud.tinystore.inventory.interfaces.dto;

import lombok.Data;

@Data
public class ReserveResponse {

	private String reservationId;
	private String shopId;
	private String skuId;
	private Integer quantity;
	private Long expireAtEpochSeconds;
	private boolean idempotent;
}

