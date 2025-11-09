package com.github.spud.tinystore.order.interfaces.dto.request;

import com.github.spud.tinystore.order.application.command.user.ConfirmOrderCommand;
import com.github.spud.tinystore.order.interfaces.dto.ShopProductDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import lombok.Data;

/**
 * @author Spud
 * @date 2025/9/2
 */
@Data
public class PreviewOrderRequest {

	@Size(min = 1, message = "结算单中缺少商品清单")
	private List<ShopProductDto> items;

	private Set<String> coupons;

	@NotNull(message = "结算单中缺少配送信息")
	private String addressId;

	@NotBlank(message = "请求中缺少设备ID")
	private String deviceId;

	public ConfirmOrderCommand toCommand(String userId) {
		// TODO: map to command
		return ConfirmOrderCommand
			.builder()
			.build();
	}


}
