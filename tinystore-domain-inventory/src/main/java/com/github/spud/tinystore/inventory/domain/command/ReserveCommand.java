package com.github.spud.tinystore.inventory.domain.command;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReserveCommand {

	@NotBlank
	String shopId;
	@NotBlank
	String skuId;
	@Min(1)
	long quantity;
	@Min(1)
	int expireSeconds;
	String operationId; // 可空
}

