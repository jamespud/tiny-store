package com.github.spud.tinystore.product.interfaces.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class ProductResponseDTO {

	private String id;
	private String name;
	private String categoryId;
	private String description;
	private String status;
	private List<String> tags;
	private Long version;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
