package com.github.spud.tinystore.inventory.domain.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdjustTotalCommand {

	@NotBlank
	String shopId;
	@NotBlank
	String skuId;
	long delta; // 可正可负
	String reason;
}

