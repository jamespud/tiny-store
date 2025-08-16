package com.github.spud.tinystore.order.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * @author Spud
 * @date 2025/8/12
 */
@Data
public class Settlement {

	@Size(min = 1, message = "结算单中缺少商品清单")
	private Collection<Item> items;

	@NotNull(message = "结算单中缺少配送信息")
	private Purchase purchase;

	private List<Coupon> coupons;

	@NotBlank(message = "Idempotency key cannot be blank")
	@Size(min = 1, max = 64, message = "Idempotency key must be between 1 and 64 characters")
	// 用于幂等性控制
	private String idempotencyKey;

	/**
	 * 购物清单中的商品信息 基于安全原因（避免篡改价格），改信息不会取客户端的，需在服务端根据商品ID再查询出来
	 */
	@JsonIgnore
	public transient Map<Long, Product> productMap;


}