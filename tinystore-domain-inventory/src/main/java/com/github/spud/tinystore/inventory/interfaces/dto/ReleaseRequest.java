package com.github.spud.tinystore.inventory.interfaces.dto;

import lombok.Data;

@Data
public class ReleaseRequest {

	private String reservationId;
	private String reason;
}
