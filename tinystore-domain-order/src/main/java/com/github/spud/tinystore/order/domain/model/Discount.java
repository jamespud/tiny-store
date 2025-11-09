package com.github.spud.tinystore.order.domain.model;

/**
 * 店铺优惠、平台优惠等
 */
public record Discount(String id, String description, Money amount, Money threshold,
											 String startTime, String endTime) {

}
