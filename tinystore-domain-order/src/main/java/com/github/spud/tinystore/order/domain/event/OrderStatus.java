package com.github.spud.tinystore.order.domain.event;

import java.util.*;

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
	// 新建
	CREATED("CREATED", "已创建", Category.CORE_FLOW, false),

	// 用户完成下单（选品、填地址）但未支付，或支付未成功（如扣款失败）。
	UNPAID("UNPAID", "待支付", Category.CORE_FLOW, false),

	// 用户完成全款支付（或预售尾款支付），资金已到账（字节支付 / 第三方支付确认）。
	PAID("PAID", "已支付", Category.CORE_FLOW, false),

	// 已支付后，商家未开始发货操作（如未接单、未出库），等待商家执行发货流程。
	FULFILLMENT_PENDING("FULFILLMENT_PENDING", "待履约", Category.CORE_FLOW, false),

	// 商家已完成发货操作，物流环节已启动（如物流揽收、运输、派送），未到签收环节。
	FULFILLING("FULFILLING", "履约中", Category.CORE_FLOW, false),

	// 订单正常结束：用户签收商品且无售后（或售后已完成），或系统自动确认完成（如 7 天无售后）。
	COMPLETED("COMPLETED", "已完成", Category.CORE_FLOW, true),

	// 用户申请取消订单（如未发货时申请取消），等待商家 / 平台审核，未最终确认取消。
	CANCELLING("CANCELLING", "取消中", Category.CORE_FLOW, false),

	// 订单终止：可能是 “待支付超时”“取消申请审核通过”“订单无效”（如违规订单）。
	CANCELLED("CANCELLED", "已取消", Category.CORE_FLOW, true),

	//用户发起售后（退款、换货、维修），且售后流程未结束（如审核中、退货中）。
	AFTER_SALE("AFTER_SALE", "售后中", Category.CORE_FLOW, false),

	// 特殊场景订单终止：如订单信息无效（地址错误、商品下架）、系统判定违规（刷单），由平台强制关闭。
	CLOSED("CLOSED", "已关闭", Category.CORE_FLOW, true),


	// FULFILLMENT
	// 用户已支付，订单已同步至商家后台，但商家未点击 “确认接单”（如商家未查看、系统未自动接单）。
	ACCEPT_PENDING("ACCEPT_PENDING", "待商家接单", Category.FULFILLMENT, false),

	// 商家确认接收订单（手动点击或系统自动接单，如 “48 小时自动接单” 规则），准备备货。
	ACCEPTED("ACCEPTED", "商家已接单", Category.FULFILLMENT, false),

	// 商家已接单，商品已在仓库备货，但未完成 “出库” 操作（未生成物流单号）。
	SHIP_PENDING("SHIP_PENDING", "待发货", Category.FULFILLMENT, false),

	// 商家完成仓库出库操作（商品从库存中调出，打包完成），已生成物流单号但未同步给物流。
	SHIPPED("SHIPPED", "已发货", Category.FULFILLMENT, false),

	// 商家已同步物流单号至物流平台，等待物流公司上门取件（如顺丰、中通取件）。
	PICK_UP_PENDING("PICK_UP_PENDING", "待揽件", Category.FULFILLMENT, false),

	// 物流公司上门取件完成，商品正式进入物流运输环节（物流系统更新 “揽收成功”）。
	PICKED_UP("PICKED_UP", "已揽收", Category.FULFILLMENT, false),

	// 商品在物流中转环节（如从商家所在地仓库运往用户所在城市的中转仓），未到派送环节。
	TRANSITING("TRANSITING", "运输中", Category.FULFILLMENT, false),

	// 商品已到达用户所在城市的派送点，物流员正在安排配送（如 “今日 18:00 前送达”）。
	DELIVERING("DELIVERING", "派送中", Category.FULFILLMENT, false),

	// 物流员已到达配送地址（如小区门口、快递柜），等待用户取件或确认签收。
	OUT_FOR_DELIVERY("OUT_FOR_DELIVERY", "待签收", Category.FULFILLMENT, false),

	// 用户确认收货（手动点击 “确认签收” 或物流系统自动确认，如快递柜取件后 24 小时）。
	DELIVERED("DELIVERED", "已签收", Category.FULFILLMENT, false),

	// 履约环节出现问题：如商家缺货无法发货、物流停滞（超 48 小时无更新）、地址错误无法配送。
	FULFILLMENT_EXCEPTION("FULFILLMENT_EXCEPTION", "履约异常", Category.FULFILLMENT, false),

	// PAYMENT
	// 用户下单后未点击 “支付”，或点击后未完成付款（如跳转支付宝后未输入密码）。
	PAYMENT_PENDING("PAYMENT_PENDING", "待支付", Category.PAYMENT, false),

	// 用户已发起支付请求（如点击 “微信支付”），但第三方支付平台未反馈扣款结果（如网络延迟）。
	PAYMENT_PROCESSING("PAYMENT_PROCESSING", "支付处理中", Category.PAYMENT, false),

	// 第三方支付平台（微信、支付宝、字节支付）确认扣款完成，资金已转入担保账户。
	PAYMENT_PAID("PAYMENT_PAID", "已支付", Category.PAYMENT, false),

	// 扣款未成功（如余额不足、银行卡冻结、风控拦截），支付平台反馈 “失败”。
	PAYMENT_FAILED("PAYMENT_FAILED", "支付失败", Category.PAYMENT, false),

	// 用户申请退款（如取消订单、售后退款），平台已受理但未完成资金退回（如审核中、银行处理中）。
	REFUNDING("REFUNDING", "退款中", Category.PAYMENT, false),

	// 退款资金已退回用户原支付账户（如微信零钱、银行卡），支付平台反馈 “退款完成”。
	REFUNDED("REFUNDED", "已退款", Category.PAYMENT, false),

	// 退款流程异常（如用户银行卡已注销、银行系统故障），资金无法退回。
	REFUND_EXCEPTION("REFUND_EXCEPTION", "退款异常", Category.PAYMENT, false),

	// AFTER-SALE
	// 用户提交售后申请（填写原因、上传凭证），但平台 / 商家未开始审核。
	AFTER_SALE_PENDING("AFTER_SALE_PENDING", "售后待处理", Category.AFTER_SALE, false),

	// 平台 / 商家已接收售后申请，正在核查订单信息、凭证（如 “是否已签收”“商品是否影响二次销售”）。
	AFTER_SALE_AUDITING("AFTER_SALE_AUDITING", "售后处理中", Category.AFTER_SALE, false),

	// 平台 / 商家确认售后申请有效（如 “7 天无理由退货符合规则”），进入下一步处理。
	AFTER_SALE_APPROVED("AFTER_SALE_APPROVED", "售后已同意", Category.AFTER_SALE, false),

	// 售后申请不符合规则（如 “超过 7 天无理由期限”“商品已使用且无质量问题”），售后终止。
	AFTER_SALE_REJECTED("AFTER_SALE_REJECTED", "售后拒绝", Category.AFTER_SALE, false),

	// 审核通过且需用户寄回商品（如 “退货退款”“换货”），等待用户发出退货包裹。
	RETURN_PENDING("RETURN_PENDING", "待用户退货", Category.AFTER_SALE, false),

	// 用户已寄出退货包裹，物流系统显示 “已揽收” 但未到达商家仓库。
	RETURNING("RETURNING", "用户退货中", Category.AFTER_SALE, false),

	// 退货包裹已到达商家仓库，商家未开箱核查商品（如 “是否破损”“是否为原商品”）。
	RETURN_RECEIVED("RETURN_RECEIVED", "商家待验货", Category.AFTER_SALE, false),

	// 商家确认退货商品符合要求（无破损、不影响二次销售），准备处理退款 / 换货。
	RETURN_APPROVED("RETURN_APPROVED", "商家已同意退货", Category.AFTER_SALE, false),

	// 商家确认退货商品不符合要求（如 “已破损”“非原订单商品”），拒绝继续售后。
	RETURN_REJECTED("RETURN_REJECTED", "商家拒绝退货", Category.AFTER_SALE, false),

	// 验货通过后，正在执行售后动作：退款（平台打款）、换货（商家备新货）。
	RETURN_PROCESSING("RETURN_PROCESSING", "售后处理中", Category.AFTER_SALE, false),

	// 售后动作执行完毕：退款到账、换货商品已发货。
	AFTER_SALE_COMPLETED("AFTER_SALE_COMPLETED", "售后已完成", Category.AFTER_SALE, false),

	// 售后流程终止（如用户主动取消申请、验货拒绝后协商失败）。
	AFTER_SALE_CLOSED("AFTER_SALE_CLOSED", "售后已关闭", Category.AFTER_SALE, true),


	PAYMENT_EXPIRED("PAYMENT_EXPIRED", "支付过期", Category.PAYMENT, true),
	PACKING("PACKING", "打包中", Category.FULFILLMENT, false),
	PARTIALLY_SHIPPED("PARTIALLY_SHIPPED", "部分发货", Category.FULFILLMENT, false),
	REFUND_PENDING("REFUND_PENDING", "待退款", Category.PAYMENT, false),
	RETURN_REQUESTED("RETURN_REQUESTED", "申请退货", Category.AFTER_SALE, false),
	PARTIALLY_REFUNDED("PARTIALLY_REFUNDED", "部分退款", Category.PAYMENT, false),
	RETURNED("RETURNED", "已退货", Category.AFTER_SALE, false);

	/**
	 * 检查当前状态是否为终态
	 *
	 * @return true 如果是终态，false 否则
	 */
	public boolean isTerminal() {
		return this == COMPLETED || this == CANCELLED;
	}

	/**
	 * 获取状态的中文描述
	 *
	 * @return 状态的中文名称
	 */
	public String getChineseName() {
		return switch (this) {
			case UNPAID -> "待支付";
			case PAID -> "已支付";
			case FULFILLMENT_PENDING -> "待履约";
			case FULFILLING -> "履约中";
			case COMPLETED -> "已完成";
			case CANCELLED -> "已取消";
			default -> "";
		};
	}

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