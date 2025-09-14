package com.github.spud.tinystore.product.interfaces.dto;

import lombok.Data;

@Data
public class ConfirmRequest {
	private String reservationId;
	private String correlationId;
}

