package com.github.spud.tinystore.order.domain.service;

import cn.hutool.core.util.IdUtil;

/**
 * 业务 ID 生成器。
 *
 * <p>trade / order / paymentIntent 的 ID 均为 opaque 字符串，且必须在多副本下全局唯一。
 * 因此统一使用无节点状态的 UUID（{@link IdUtil#fastSimpleUUID()}，32 位无连字符十六进制），
 * 彻底消除 Snowflake 的 datacenterId / workerId / 时钟回拨这一整类节点状态依赖与碰撞风险。
 */
public final class TradeIdGenerator {

    private TradeIdGenerator() {
    }

    public static String generateTradeId() {
        return IdUtil.fastSimpleUUID();
    }

    public static String generateOrderId() {
        return IdUtil.fastSimpleUUID();
    }

    public static String generatePaymentIntentId() {
        return IdUtil.fastSimpleUUID();
    }
}
