package com.github.spud.tinystore.order.interfaces.dto.request;

import com.github.spud.tinystore.order.application.command.PreviewOrderCommand;
import com.github.spud.tinystore.order.interfaces.dto.ProductItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Collection;
import lombok.Data;

/**
 * @author Spud
 * @date 2025/9/2
 */
@Data
public class PreviewOrderRequest {

	@Size(min = 1, message = "结算单中缺少商品清单")
	private Collection<ProductItem> items;

	@NotNull(message = "结算单中缺少配送信息")
	private String addressId;

	@NotBlank(message = "请求中缺少设备ID")
	private String deviceId;
	
	public PreviewOrderCommand toCommand(String userId) {
		PreviewOrderCommand command = new PreviewOrderCommand();
		return command;
	}

}
