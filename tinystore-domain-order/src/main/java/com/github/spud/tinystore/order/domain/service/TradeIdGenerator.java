package com.github.spud.tinystore.order.domain.service;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;

public class TradeIdGenerator {

    private static Integer GROUP_ID = Integer.parseInt(System.getProperty("GROUP_ID", "1"));
    private static Integer CENTER_ID = Integer.parseInt(System.getProperty("CENTER_ID", "1"));

    private static Snowflake snowflake = IdUtil.getSnowflake(GROUP_ID, CENTER_ID);

    public static String generateTradeId() {
        return snowflake.nextIdStr();
    }

    public static String generateOrderId() {
        return snowflake.nextIdStr();
    }
    
    public static String generatePaymentIntentId() {
        return snowflake.nextIdStr();
    }

}
