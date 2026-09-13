package com.github.spud.tinystore.order.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.github.spud.tinystore.order.application.command.CreateTradeCommand;

/**
 * create-trade 请求指纹（SHA-256）。
 *
 * <p>幂等键只表示"同一个请求"，因此必须把**请求体本身**也绑定进幂等记录：
 * 同一个幂等键配不同请求体时，服务端要能识别为冲突（409），而不是把上一次的结果返回给调用方。
 *
 * <p>指纹只包含会影响下单结果的业务输入：buyerId、addressId、规范化后的 orderLines、
 * platformCouponCodes、shopCouponCodesByShop，以及**客户端显式传入**的 tradeId。
 *
 * <p>刻意不包含：服务端生成的 tradeId（调用前还不存在）、traceId、时间戳——它们变化不应该
 * 让同一个业务请求变成"不同请求"。
 *
 * <p>所有 list / map 在哈希前排序，因此请求体中元素顺序变化不会改变指纹。
 */
public final class TradeRequestFingerprint {

    private TradeRequestFingerprint() {
    }

    public static String of(CreateTradeCommand command) {
        StringBuilder canonical = new StringBuilder();
        canonical.append("buyerId=").append(nullSafe(command.getBuyerId())).append('\n');
        canonical.append("addressId=").append(nullSafe(command.getAddressId())).append('\n');

        // 客户端显式传入的 tradeId 属于请求的一部分；服务端生成的不参与。
        String clientTradeId = command.getTradeId();
        canonical.append("clientTradeId=")
                .append(clientTradeId == null || clientTradeId.isEmpty() ? "" : clientTradeId)
                .append('\n');

        canonical.append("platformCouponCodes=").append(sorted(command.getPlatformCouponCodes())).append('\n');
        canonical.append("shopCouponCodesByShop=").append(canonicalShopCoupons(command)).append('\n');

        for (String line : canonicalLines(command.getOrderLines())) {
            canonical.append(line).append('\n');
        }
        return sha256(canonical.toString());
    }

    private static List<String> canonicalLines(List<CreateTradeCommand.OrderLineCommand> lines) {
        List<String> rendered = new ArrayList<>();
        if (lines == null) {
            return rendered;
        }
        for (CreateTradeCommand.OrderLineCommand line : lines) {
            rendered.add("line|"
                    + nullSafe(line.getShopId()) + '|'
                    + nullSafe(line.getSkuId()) + '|'
                    + nullSafe(line.getProductId()) + '|'
                    + nullSafe(line.getQuantity()) + '|'
                    + nullSafe(line.getPriceCents()) + '|'
                    + nullSafe(line.getWeightGrams()));
        }
        rendered.sort(String::compareTo);
        return rendered;
    }

    private static String canonicalShopCoupons(CreateTradeCommand command) {
        Map<String, List<String>> byShop = command.getShopCouponCodesByShop();
        if (byShop == null || byShop.isEmpty()) {
            return "";
        }
        Map<String, List<String>> sorted = new TreeMap<>(byShop);
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : sorted.entrySet()) {
            parts.add(nullSafe(entry.getKey()) + "=" + sorted(entry.getValue()));
        }
        return String.join(",", parts);
    }

    private static List<String> sorted(List<String> values) {
        List<String> copy = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                copy.add(nullSafe(value));
            }
        }
        copy.sort(String::compareTo);
        return copy;
    }

    private static String nullSafe(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
