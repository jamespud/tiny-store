package com.github.spud.tinystore.order.interfaces.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 结算单中的配送信息
 *
 **/
@Data
public class Purchase {

	private Boolean delivery = true;

	@NotEmpty(message = "配送信息中缺少支付方式")
	private String pay;

	@NotEmpty(message = "配送信息中缺少收件人姓名")
	private String name;

	@NotEmpty(message = "配送信息中缺少收件人电话")
	private String telephone;

	@NotEmpty(message = "配送信息中缺少收件地址")
	private String location;
}
