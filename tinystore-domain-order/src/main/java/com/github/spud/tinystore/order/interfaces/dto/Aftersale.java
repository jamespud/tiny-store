package com.github.spud.tinystore.order.interfaces.dto;

import lombok.Data;

/**
 * 售后单
 *
 * @author Spud
 * @date 2025/9/1
 */
@Data
public class Aftersale {

	String id;

	String orderId;

	String type;

	String reason;

	String status;
}
