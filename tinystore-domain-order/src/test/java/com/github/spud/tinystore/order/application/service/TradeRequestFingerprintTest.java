package com.github.spud.tinystore.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.spud.tinystore.order.application.command.CreateTradeCommand;

/**
 * 请求指纹的归一化规则（C11）。
 *
 * <p>指纹决定"同一个幂等键配不同请求体"能否被识别为冲突，所以两条性质必须成立：
 * 业务等价但元素顺序不同的请求 → 同一个指纹；任一业务输入变化 → 不同指纹。
 */
@DisplayName("TradeRequestFingerprint — 归一化与敏感性")
class TradeRequestFingerprintTest {

    private static CreateTradeCommand.OrderLineCommand line(String shopId, String skuId, int qty, long price) {
        return CreateTradeCommand.OrderLineCommand.builder()
                .shopId(shopId)
                .skuId(skuId)
                .productId("prod-" + skuId)
                .productName("display-name")
                .sellerId("seller-" + shopId)
                .quantity(qty)
                .priceCents(price)
                .weightGrams(0L)
                .build();
    }

    private static CreateTradeCommand.CreateTradeCommandBuilder base() {
        return CreateTradeCommand.builder()
                .buyerId("buyer-1")
                .buyerNick("nick")
                .addressId("addr-1")
                .traceId("trace-1")
                .orderLines(List.of(line("SHOP_A", "SKU_A", 1, 1000L)));
    }

    @Test
    @DisplayName("orderLines 顺序变化不改变指纹")
    void lineOrder_isNormalised() {
        CreateTradeCommand first = base()
                .orderLines(List.of(line("SHOP_A", "SKU_A", 1, 1000L), line("SHOP_B", "SKU_B", 2, 2000L)))
                .build();
        CreateTradeCommand reordered = base()
                .orderLines(List.of(line("SHOP_B", "SKU_B", 2, 2000L), line("SHOP_A", "SKU_A", 1, 1000L)))
                .build();

        assertThat(TradeRequestFingerprint.of(first)).isEqualTo(TradeRequestFingerprint.of(reordered));
    }

    @Test
    @DisplayName("券列表顺序变化不改变指纹")
    void couponOrder_isNormalised() {
        CreateTradeCommand first = base()
                .platformCouponCodes(List.of("CPN-1", "CPN-2"))
                .shopCouponCodesByShop(Map.of("SHOP_A", List.of("S-1", "S-2")))
                .build();
        CreateTradeCommand reordered = base()
                .platformCouponCodes(List.of("CPN-2", "CPN-1"))
                .shopCouponCodesByShop(Map.of("SHOP_A", List.of("S-2", "S-1")))
                .build();

        assertThat(TradeRequestFingerprint.of(first)).isEqualTo(TradeRequestFingerprint.of(reordered));
    }

    @Test
    @DisplayName("数量 / 地址 / 券 / 客户端 tradeId 任一变化都必须改变指纹")
    void businessInputs_areSensitive() {
        String baseline = TradeRequestFingerprint.of(base().build());

        assertThat(TradeRequestFingerprint.of(base()
                .orderLines(List.of(line("SHOP_A", "SKU_A", 2, 1000L))).build()))
                .as("quantity").isNotEqualTo(baseline);
        assertThat(TradeRequestFingerprint.of(base().addressId("addr-2").build()))
                .as("addressId").isNotEqualTo(baseline);
        assertThat(TradeRequestFingerprint.of(base().platformCouponCodes(List.of("CPN-1")).build()))
                .as("platform coupon").isNotEqualTo(baseline);
        assertThat(TradeRequestFingerprint.of(base().shopCouponCodesByShop(Map.of("SHOP_A", List.of("S-1"))).build()))
                .as("shop coupon").isNotEqualTo(baseline);
        assertThat(TradeRequestFingerprint.of(base().tradeId("client-trade-1").build()))
                .as("client supplied tradeId").isNotEqualTo(baseline);
        assertThat(TradeRequestFingerprint.of(base().orderLines(List.of(line("SHOP_A", "SKU_A", 1, 1001L))).build()))
                .as("price").isNotEqualTo(baseline);
    }

    @Test
    @DisplayName("traceId 不参与指纹（每次请求都不同，不应制造假冲突）")
    void traceIdIsIgnored() {
        String baseline = TradeRequestFingerprint.of(base().build());

        assertThat(TradeRequestFingerprint.of(base().traceId("trace-2").build()))
                .as("traceId must not affect the fingerprint").isEqualTo(baseline);
    }

    @Test
    @DisplayName("P1-3: sellerId/buyerNick/productName 参与指纹 —— 同键换 body 必须 409")
    void businessFieldsBeyondPriceAreBound() {
        String baseline = TradeRequestFingerprint.of(base().build());

        // sellerId 会被直接写进 ShopOrder，绝不是展示字段：换一个卖家必须算另一个请求体。
        assertThat(TradeRequestFingerprint.of(base()
                .orderLines(List.of(CreateTradeCommand.OrderLineCommand.builder()
                    .shopId("SHOP_A").skuId("SKU_A").productId("prod-SKU_A").productName("display-name")
                    .sellerId("seller-OTHER").quantity(1).priceCents(1000L).weightGrams(0L).build()))
                .build()))
            .as("sellerId").isNotEqualTo(baseline);

        assertThat(TradeRequestFingerprint.of(base().buyerNick("other-nick").build()))
            .as("buyerNick (written into Trade)").isNotEqualTo(baseline);

        assertThat(TradeRequestFingerprint.of(base()
                .orderLines(List.of(CreateTradeCommand.OrderLineCommand.builder()
                    .shopId("SHOP_A").skuId("SKU_A").productId("prod-SKU_A").productName("renamed")
                    .sellerId("seller-SHOP_A").quantity(1).priceCents(1000L).weightGrams(0L).build()))
                .build()))
            .as("productName").isNotEqualTo(baseline);
    }
}
