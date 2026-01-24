package com.github.spud.tinystore.inventory.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class StockCommitRequest {

	@NotBlank
	private String tenantId;

	@NotBlank
	private String orderNo;

	private String payNo;

	private Long paidAtEpochMs;

	@NotEmpty
	@NotNull
	private List<String> preOccupyIds;
}

