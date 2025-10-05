package com.github.spud.tinystore.product.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
import lombok.Data;

@Data
public class SkuCreateDTO {

	@NotBlank(message = "Specification combination is required")
	private String specCombination;

	@NotNull(message = "Price is required")
	private BigDecimal price;

	private Integer stock;

	private String barCode;

	private Map<String, Object> attributes;
}
