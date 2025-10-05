package com.github.spud.tinystore.product.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * ProductCreateDTO - Request DTO for creating a product
 * <p>
 * Validation: - name: Required, 1-500 characters - categoryId: Required - description: Optional,
 * max 5000 characters - tags: Optional list
 */
@Data
public class ProductCreateDTO {

	@NotBlank(message = "Product name is required")
	@Size(min = 1, max = 500, message = "Product name must be between 1 and 500 characters")
	private String name;

	@NotBlank(message = "Category ID is required")
	private String categoryId;

	@Size(max = 5000, message = "Description cannot exceed 5000 characters")
	private String description;

	private List<String> tags;

	// TODO: Add additional fields as needed:
	// - images: List<String>
	// - attributes: Map<String, Object>
	// - brand: String
	// - etc.
}
