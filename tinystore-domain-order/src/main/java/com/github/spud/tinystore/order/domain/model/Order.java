package com.github.spud.tinystore.order.domain.model;

import com.github.spud.tinystore.order.domain.status.AfterSaleStatus;
import com.github.spud.tinystore.order.domain.status.CancellationStatus;
import com.github.spud.tinystore.order.domain.status.FulfillmentStatus;
import com.github.spud.tinystore.order.domain.status.PaymentStatus;
import com.github.spud.tinystore.order.domain.event.OrderDomainEvent;
import java.util.List;
import lombok.Data;

/**
 * @author Spud
 * @date 2025/9/1
 */
@Data
public class Order {

	private String id;
	private Buyer buyer;
	private List<OrderLine> lines;
	private List<ChargeItem> charges;
	private List<DiscountAllocation> discounts;
	private PricingSummary pricingSummary;
	private Address shippingAddress;
	private PaymentStatus paymentStatus;
	private FulfillmentStatus fulfillmentStatus;
	private CancellationStatus cancellationStatus;
	private AfterSaleStatus afterSaleStatus;
	private List<ReservationRef> reservations;
	private Integer version;
	private List<OrderDomainEvent> orderDomainEvents;
}


