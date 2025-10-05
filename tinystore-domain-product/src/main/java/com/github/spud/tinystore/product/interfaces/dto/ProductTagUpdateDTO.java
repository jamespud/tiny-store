package com.github.spud.tinystore.product.interfaces.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class ProductTagUpdateDTO {

	@NotNull
	private List<String> tags;
}
