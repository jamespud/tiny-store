package com.github.spud.tinystore.infrastructure.common.constant;

import org.springframework.context.annotation.Configuration;

@Configuration
public class MessageTopicConfig {
	
	public static final String DECREASE_STOCK_TOPIC = "topic-stock-decrease";
	public static final String INCREASE_STOCK_TOPIC = "topic-stock-increase";
	public static final String FROZEN_STOCK_TOPIC = "topic-stock-frozen";
	public static final String THAWED_STOCK_TOPIC = "topic-stock-thawed";
	
	public static final String PAYMENT_DELAY_TOPIC = "topic-payment-delay";
	
	
	public static final String STOCK_KEY_PREFIX = "product:stock:";
	public static final String STOCK_LOCK_PREFIX = "lock:stock:";
	public static final String PAYMENT_LOCK_PREFIX = "lock:payment:";

}
