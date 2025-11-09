package com.github.spud.tinystore.order.domain.enums;

// SubOrderStatus.java（子订单状态）
public enum SubOrderStatus {
	SUB_PENDING_PAY("子订单待支付", 10),  // 主订单未支付时子订单状态
	SUB_PAID("子订单已支付", 20),        // 主订单支付后同步
	SUB_SHIPPED("子订单已发货", 30),      // 商家发货后
	SUB_COMPLETED("子订单已完成", 40),    // 用户确认收货/自动确认
	SUB_CANCELED("子订单已取消", 50),     // 主订单取消后同步
	SUB_REFUNDED("子订单已退款", 60);     // 子订单单独退款/主订单全额退款
	private final String desc;
	private final int code;

	SubOrderStatus(String desc, int code) {
		this.desc = desc;
		this.code = code;
	}

	// Getter
	public String getDesc() {
		return desc;
	}

	public int getCode() {
		return code;
	}
}