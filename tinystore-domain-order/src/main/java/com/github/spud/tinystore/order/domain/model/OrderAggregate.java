package com.github.spud.tinystore.order.domain.model;

import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Order Aggregate Root - Rich Domain Model
 * Encapsulates order business logic, invariants, and state transitions
 *
 * @author Spud
 * @date 2025/9/1
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAggregate {

	private Buyer buyer;
	private List<SubOrder> subOrders;
	private List<Coupon> coupons;
	private List<Discount> discounts;
	private List<CouponAllocation> couponAllocations;
	private List<DiscountAllocation> discountAllocations;
	private PricingSummary pricingSummary;
	private List<ReservationRef> reservations;
	// Enhanced fields for rich aggregate
	private Address address;
	private Map<String, String> attributes; // Flexible extension
	private boolean calculated;

	public OrderAggregate(Buyer buyer, List<SubOrder> subOrders, List<Coupon> coupons, List<Discount> discounts, Address address) {
		this.buyer = buyer;
		this.subOrders = subOrders;
		this.coupons = coupons;
		this.discounts = discounts;
		this.address = address;
		this.couponAllocations = new ArrayList<>();
		this.discountAllocations = new ArrayList<>();
		this.reservations = new ArrayList<>();
	}

	public void computePricing() {
	}

	// Argument classes (inner classes for now, can be moved to separate files)
	@Data
	@Builder
	public static class CreateOrderArgs {
		private Buyer buyer;
		private List<SubOrder> subOrders;
		private List<Coupon> coupons;
		private List<Discount> discounts;
		private Address address;
	}

	@Data
	@Builder
	public static class PaymentSuccessArgs {
		private String paymentId;
		private BigDecimal amount;
		private boolean isDeposit;
		private boolean isFinalPayment;
	}

	@Data
	@Builder
	public static class ShipArgs {
		private String subOrderId;
		private String shipmentInfo;
	}

	@Data
	@Builder
	public static class DeliveredArgs {
		private String shipmentInfo;
		private boolean afterSaleWindowOpen;
	}

	@Data
	@Builder
	public static class RepriceArgs {
		private PricingSummary newPricingSummary;
		private List<CouponAllocation> newCouponAllocations;
		private List<DiscountAllocation> newDiscountAllocations;
	}
}


