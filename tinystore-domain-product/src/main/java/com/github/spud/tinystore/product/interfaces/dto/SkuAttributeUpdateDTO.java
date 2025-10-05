package com.github.spud.tinystore.product.interfaces.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Data;

@Data
public class SkuAttributeUpdateDTO {

	@NotNull
	private Map<String, Object> attributes;
}
