package com.github.spud.tinystore.inventory.interfaces.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.Data;

@Data
public class StockRestockRequest {

	@NotBlank
	private String tenantId;

	@NotBlank
	private String orderNo;

	@NotBlank
	private String refundId;

	@NotEmpty
	@NotNull
	@Valid
	private List<Line> items;

	@Data
	public static class Line {
		@NotBlank
		private String skuId;
		@NotNull
		@Positive
		private Integer quantity;
	}
}

