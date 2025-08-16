package com.github.spud.tinystore.order.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author Spud
 * @date 2025/8/12
 **/
@Data
public class Item {

	@NotNull(message = "结算单中必须有明确的商品数量")
	@Min(value = 1, message = "结算单中商品数量至少为一件")
	private Integer amount;

	@JsonProperty("id")
	@NotNull(message = "结算单中必须有明确的商品信息")
	private Long productId;
}
