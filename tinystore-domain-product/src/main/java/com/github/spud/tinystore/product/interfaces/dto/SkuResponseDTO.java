package com.github.spud.tinystore.product.interfaces.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

@Data
public class SkuResponseDTO {

	private String id;
	private String productId;
	private String specCombination;
	private BigDecimal price;
	private Integer stock;
	private String barCode;
	private String status;
	private Map<String, Object> attributes;
	private Long version;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
