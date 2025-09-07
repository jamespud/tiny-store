package com.github.spud.tinystore.order.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author Spud
 * @date 2025/8/15
 */
@Data
public class Coupon {

	@JsonProperty("id")
	@NotNull(message = "结算单中必须有明确的优惠券信息")
	private Long couponId;

	@DecimalMin(value = "0.0", inclusive = false, message = "结算单中优惠券折扣金额不能为负数")
	@NotNull(message = "结算单中必须有明确的优惠券折扣金额")
	private Double discount; // 折扣金额

}
