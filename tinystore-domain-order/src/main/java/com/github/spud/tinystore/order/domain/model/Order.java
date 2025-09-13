package com.github.spud.tinystore.order.domain.model;

import com.github.spud.tinystore.order.domain.event.OrderDomainEvent;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * @author Spud
 * @date 2025/9/1
 */
@Data
@Builder
public class Order {

	private Buyer buyer;

	private List<OrderLine> lines;
	
	private Map<Product, Integer> products;

	private List<Coupon> coupons;

	private List<Discount> discounts;

	private List<CouponAllocation> couponAllocations;

	private List<DiscountAllocation> discountAllocations;

	private PricingSummary pricingSummary;

	private List<ReservationRef> reservations;

	private Integer version;

	private List<OrderDomainEvent> orderDomainEvents;

	private String deviceId;
	
	private boolean calculated;
	
	private Address address;
	
	private Order() {
		
	}
}


