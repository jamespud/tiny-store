package com.github.spud.tinystore.order.domain.service;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TradeIdGenerator {

    private static Integer GROUP_ID = Integer.parseInt(System.getProperty("GROUP_ID", "1"));
    private static Integer CENTER_ID = Integer.parseInt(System.getProperty("CENTER_ID", "1"));
    private static final Object ID_LOCK = new Object();

    private static Snowflake snowflake = IdUtil.getSnowflake(GROUP_ID, CENTER_ID);

    public static String generateTradeId() {
        return nextIdStr();
    }

    public static String generateOrderId() {
        return nextIdStr();
    }
    
    public static String generatePaymentIntentId() {
        return nextIdStr();
    }

    private static String nextIdStr() {
        synchronized (ID_LOCK) {
            try {
                return snowflake.nextIdStr();
            } catch (RuntimeException ex) {
                if (!isClockRollback(ex)) {
                    throw ex;
                }
                log.warn("Snowflake clock rollback detected, falling back to UUID id generation: {}",
                    ex.getMessage());
                snowflake = IdUtil.getSnowflake(GROUP_ID, CENTER_ID);
                return IdUtil.fastSimpleUUID();
            }
        }
    }

    private static boolean isClockRollback(RuntimeException ex) {
        String message = ex.getMessage();
        return message != null && message.contains("Clock moved backwards");
    }

}
