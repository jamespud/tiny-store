package com.github.spud.tinystore.order.domain.service;

public class TradeIdGenerator {

	public static String generate() {
		// 示例实现：使用当前时间戳和随机数生成唯一交易ID
		long timestamp = System.currentTimeMillis();
		int randomNum = (int) (Math.random() * 100000);
		return "T" + timestamp + String.format("%05d", randomNum);
	}
	
	public static String generateForShop(String shopId) {
		// 示例实现：在交易ID中包含店铺ID前缀
		long timestamp = System.currentTimeMillis();
		int randomNum = (int) (Math.random() * 100000);
		return "T" + shopId + timestamp + String.format("%05d", randomNum);
	}

}
