package com.github.spud.tinystore.inventory.interfaces.dto;

import cn.hutool.core.lang.Pair;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

import lombok.Data;

@Data
public class StockReleaseRequest {

	@NotBlank
	private String shopId;

	@NotBlank
	private String tradeId;

	@NotBlank
	private String reason;

	@NotEmpty
	@NotNull
	private List<String> preOccupyIds;
    
    private List<Pair<String, String>> skuIdOccupyIdPairs;
}

