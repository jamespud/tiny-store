package com.github.spud.tinystore.order.domain.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 订单支付成功事件
 * 当订单支付成功后触发此事件
 *
 * @author Spud
 * @date 2025/9/29
 */
@Data
@AllArgsConstructor
public class OrderPaidEvent extends OrderDomainBaseEvent {
	private final String paymentId;
	private final BigDecimal amount;
	private final boolean isDeposit;
	private final boolean isFinalPayment;
	
	public OrderPaidEvent(String orderNo, String paymentId, BigDecimal amount, boolean isDeposit, boolean isFinalPayment) {
		this.setEventId(UUID.randomUUID().toString().replace("-", ""));
		this.setOrderId(orderNo);
		this.setOccurredAt(OffsetDateTime.now());
		this.paymentId = paymentId;
		this.amount = amount;
		this.isDeposit = isDeposit;
		this.isFinalPayment = isFinalPayment;
		this.setTraceId(MDC.get("traceId"));
	}
	
	@Override
	public Map<String, Object> getPayload() {
		Map<String, Object> payload = new HashMap<>();
		payload.put("paymentId", paymentId);
		payload.put("amount", amount);
		payload.put("isDeposit", isDeposit);
		payload.put("isFinalPayment", isFinalPayment);
		return payload;
	}
}