package com.github.spud.tinystore.order.domain.event;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 优化后的订单状态枚举（仍为单枚举示例；推荐后续拆分多维状态） 说明： 1. code: 机器可读 2. label: 展示名称（可后续 i18n） 3. category: 用于聚合/分组 4.
 * terminal: 是否主流程终态（不含售后继续维度） 5. transitions: 合法迁移
 * <p>
 * 如需更细颗粒，建议拆成 PaymentStatus / FulfillmentStatus / AfterSaleStatus 并用聚合逻辑动态生成对外 OrderViewStatus。
 *
 * @author Spud
 * @date 2025/8/29
 */
public enum OrderStatus {

	// CORE FLOW
	CREATED("CREATED", "已创建", Category.CORE_FLOW, false),
	PAYMENT_PROCESSING("PAYMENT_PROCESSING", "支付处理中", Category.PAYMENT, false),
	PAID("PAID", "已支付", Category.CORE_FLOW, false),

	ACCEPT_PENDING("ACCEPT_PENDING", "待接单", Category.FULFILLMENT, false),
	ACCEPTED("ACCEPTED", "已接单", Category.FULFILLMENT, false),
	PACKING("PACKING", "备货中", Category.FULFILLMENT, false),

	SHIP_PENDING("SHIP_PENDING", "待发货", Category.FULFILLMENT, false),
	PARTIALLY_SHIPPED("PARTIALLY_SHIPPED", "部分发货", Category.FULFILLMENT, false),
	SHIPPED("SHIPPED", "已发货", Category.FULFILLMENT, false),
	DELIVERED("DELIVERED", "已送达", Category.CORE_FLOW, false),
	COMPLETED("COMPLETED", "已完成", Category.CORE_FLOW, true),

	// ABNORMAL / CLOSE
	PAYMENT_FAILED("PAYMENT_FAILED", "支付失败", Category.ABNORMAL, false),
	PAYMENT_EXPIRED("PAYMENT_EXPIRED", "支付超时", Category.ABNORMAL, true),
	CANCELLED("CANCELLED", "已取消", Category.CLOSED, true),
	CLOSED("CLOSED", "已关闭", Category.CLOSED, true),

	// REFUND / AFTER-SALE
	REFUND_PENDING("REFUND_PENDING", "退款待处理", Category.AFTER_SALE, false),
	REFUNDING("REFUNDING", "退款中", Category.AFTER_SALE, false),
	PARTIALLY_REFUNDED("PARTIALLY_REFUNDED", "部分退款", Category.AFTER_SALE, false),
	REFUNDED("REFUNDED", "已退款", Category.AFTER_SALE, true),

	RETURN_REQUESTED("RETURN_REQUESTED", "退货申请中", Category.AFTER_SALE, false),
	RETURN_APPROVED("RETURN_APPROVED", "退货已同意", Category.AFTER_SALE, false),
	RETURN_REJECTED("RETURN_REJECTED", "退货拒绝", Category.AFTER_SALE, false),
	RETURNED("RETURNED", "已退货", Category.AFTER_SALE, false),

	AFTER_SALE_CLOSED("AFTER_SALE_CLOSED", "售后结束", Category.AFTER_SALE, false);

	public enum Category {
		CORE_FLOW, PAYMENT, FULFILLMENT, AFTER_SALE, ABNORMAL, CLOSED
	}

	private final String code;
	private final String label;
	private final Category category;
	private final boolean terminal;

	OrderStatus(String code, String label, Category category, boolean terminal) {
		this.code = code;
		this.label = label;
		this.category = category;
		this.terminal = terminal;
	}

	public String getCode() {
		return code;
	}

	public String getLabel() {
		return label;
	}

	public Category getCategory() {
		return category;
	}

	public boolean isTerminal() {
		return terminal;
	}

	// 定义可迁移关系（精简示例，可配置化）
	private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS;

	static {
		Map<OrderStatus, Set<OrderStatus>> m = new EnumMap<>(OrderStatus.class);

		put(m, CREATED, PAYMENT_PROCESSING, CANCELLED, PAYMENT_EXPIRED, CLOSED);
		put(m, PAYMENT_PROCESSING, PAID, PAYMENT_FAILED, PAYMENT_EXPIRED, CANCELLED);
		put(m, PAYMENT_FAILED, PAYMENT_PROCESSING, CANCELLED, CLOSED);
		put(m, PAID, ACCEPT_PENDING, SHIP_PENDING, REFUND_PENDING, CANCELLED);
		put(m, ACCEPT_PENDING, ACCEPTED, PACKING, CANCELLED);
		put(m, ACCEPTED, PACKING, SHIP_PENDING, CANCELLED);
		put(m, PACKING, SHIP_PENDING, PARTIALLY_SHIPPED, SHIPPED, CANCELLED);
		put(m, SHIP_PENDING, PARTIALLY_SHIPPED, SHIPPED, CANCELLED);
		put(m, PARTIALLY_SHIPPED, SHIPPED, DELIVERED);
		put(m, SHIPPED, DELIVERED, REFUND_PENDING);
		put(m, DELIVERED, COMPLETED, RETURN_REQUESTED, REFUND_PENDING);
		put(m, COMPLETED, RETURN_REQUESTED, REFUND_PENDING);

		// 退款/退货
		put(m, REFUND_PENDING, REFUNDING, REFUNDED, PARTIALLY_REFUNDED);
		put(m, REFUNDING, REFUNDED, PARTIALLY_REFUNDED);
		put(m, PARTIALLY_REFUNDED, REFUND_PENDING, REFUNDING, REFUNDED);
		put(m, REFUNDED, AFTER_SALE_CLOSED);

		put(m, RETURN_REQUESTED, RETURN_APPROVED, RETURN_REJECTED);
		put(m, RETURN_APPROVED, RETURNED, REFUND_PENDING);
		put(m, RETURNED, REFUND_PENDING, AFTER_SALE_CLOSED);
		put(m, RETURN_REJECTED, AFTER_SALE_CLOSED);

		put(m, AFTER_SALE_CLOSED, COMPLETED); // 聚合后仍保持已完成或部分退款等

		put(m, CANCELLED, CLOSED);
		put(m, PAYMENT_EXPIRED, CLOSED);
		put(m, CLOSED); // 自环防空指针
		TRANSITIONS = Collections.unmodifiableMap(m);
	}

	private static void put(Map<OrderStatus, Set<OrderStatus>> m, OrderStatus from,
		OrderStatus... tos) {
		m.put(from, new LinkedHashSet<>(Arrays.asList(tos)));
	}

	public boolean canTransitTo(OrderStatus target) {
		if (target == null) {
			return false;
		}
		Set<OrderStatus> next = TRANSITIONS.get(this);
		return next != null && next.contains(target);
	}

	public Set<OrderStatus> nextStatuses() {
		return Optional.ofNullable(TRANSITIONS.get(this)).orElse(Collections.emptySet());
	}

	public static OrderStatus of(String code) {
		for (OrderStatus s : values()) {
			if (s.code.equalsIgnoreCase(code)) {
				return s;
			}
		}
		throw new IllegalArgumentException("Unknown order status code: " + code);
	}

	public static String graphviz() {
		// 可用于调试生成状态图
		StringBuilder sb = new StringBuilder("digraph OrderStatus { rankdir=LR;");
		TRANSITIONS.forEach(
			(k, v) -> v.forEach(t -> sb.append(k.name()).append("->").append(t.name()).append(";")));
		sb.append('}');
		return sb.toString();
	}

	@Override
	public String toString() {
		return code;
	}
}