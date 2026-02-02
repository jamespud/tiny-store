package com.github.spud.tinystore.order.test.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.spud.tinystore.inventory.application.service.StockAppService;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryStockEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryAdjustmentRepository;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryReservationRepository;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryStockRepository;
import com.github.spud.tinystore.order.domain.model.ShopOrder;
import com.github.spud.tinystore.order.domain.repository.ShopOrderRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.TradeEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.OrderLineJpaRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.ShopOrderJpaRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.TradeJpaRepository;
import com.github.spud.tinystore.payment.application.PaymentApplicationService;
import com.github.spud.tinystore.payment.infrastructure.persistence.jpa.repository.PaymentOrderJpaRepository;
import com.github.spud.tinystore.payment.infrastructure.persistence.jpa.repository.RefundRecordJpaRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.CouponEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaCouponRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MallE2EIT extends AbstractMallE2EIT {

    private static final String SHOP_A = "SHOP_A";
    private static final String SHOP_B = "SHOP_B";
    private static final String SKU_A = "SKU_A";
    private static final String SKU_B = "SKU_B";

    private String tradeId;
    private String paymentIntentId;
    private long payableAmountCents;
    private String buyerId;

    @Test
    @Order(1)
    void createTrade_preOccupyInventory() {
        seedInventory();
        cleanupOrderAndPayment();

        tradeId = UUID.randomUUID().toString();
        buyerId = "buyer-" + UUID.randomUUID();

        Map<String, Object> request = new HashMap<>();
        request.put("tradeId", tradeId);
        request.put("buyerId", buyerId);
        request.put("buyerNick", "buyer-nick");
        request.put("addressId", "addr-001");
        request.put("traceId", "trace-" + tradeId);

        List<Map<String, Object>> orderLines = new ArrayList<>();
        orderLines.add(line(SKU_A, "prod-1", "Product A", SHOP_A, "seller-A", 2, 1000L, 0L));
        orderLines.add(line(SKU_B, "prod-2", "Product B", SHOP_B, "seller-B", 1, 2000L, 0L));
        request.put("orderLines", orderLines);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Idempotency-Key", "idem-" + tradeId);
        ResponseEntity<Map> response = restTemplate.exchange(
            orderBaseUrl + "/order/trades",
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            Map.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo(0);

        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data).isNotNull();
        paymentIntentId = (String) data.get("paymentIntentId");
        payableAmountCents = ((Number) data.get("payableAmountCents")).longValue();

        ShopOrderRepository shopOrderRepository = getOrderBean(ShopOrderRepository.class);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<ShopOrder> shopOrders = shopOrderRepository.findByTradeId(tradeId);
            assertThat(shopOrders).hasSize(2);
            shopOrders.forEach(o -> assertThat(o.getInventoryPreOccupyIds()).isNotEmpty());
        });

        JpaInventoryStockRepository stockRepository = getInventoryBean(JpaInventoryStockRepository.class);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            InventoryStockEntity stockA = stockRepository.findByShopIdAndSkuId(SHOP_A, SKU_A).orElseThrow();
            InventoryStockEntity stockB = stockRepository.findByShopIdAndSkuId(SHOP_B, SKU_B).orElseThrow();
            assertThat(stockA.getReservedQuantity()).isEqualTo(2);
            assertThat(stockB.getReservedQuantity()).isEqualTo(1);
        });
    }

    @Test
    @Order(2)
    void paymentSuccess_commitInventoryAndPromotion() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("paymentIntentId", paymentIntentId);
        payload.put("tradeId", tradeId);
        payload.put("buyerId", buyerId);
        payload.put("amountCents", payableAmountCents);
        payload.put("payChannel", "TEST");
        payload.put("expireAt", LocalDateTime.now().plusMinutes(10).toString());
        payload.put("traceId", "trace-pay-" + tradeId);

        kafkaTestProducer.sendEvent("PAYMENT_INTENT_CREATED", payload);

        PaymentOrderJpaRepository paymentOrderRepository = getPaymentBean(PaymentOrderJpaRepository.class);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(paymentOrderRepository.findByPaymentIntentId(paymentIntentId)).isPresent();
        });

        PaymentApplicationService paymentApplicationService = getPaymentBean(PaymentApplicationService.class);
        paymentApplicationService.notifyOrderPaymentSuccess(paymentIntentId, tradeId, payableAmountCents, "THIRD_NO");

        TradeJpaRepository tradeRepository = getOrderBean(TradeJpaRepository.class);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            TradeEntity trade = tradeRepository.findByTradeId(tradeId).orElseThrow();
            assertThat(trade.getPayStatus()).isEqualTo("PAID");
        });

        JpaInventoryStockRepository stockRepository = getInventoryBean(JpaInventoryStockRepository.class);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            InventoryStockEntity stockA = stockRepository.findByShopIdAndSkuId(SHOP_A, SKU_A).orElseThrow();
            InventoryStockEntity stockB = stockRepository.findByShopIdAndSkuId(SHOP_B, SKU_B).orElseThrow();
            assertThat(stockA.getReservedQuantity()).isEqualTo(0);
            assertThat(stockB.getReservedQuantity()).isEqualTo(0);
            assertThat(stockA.getTotalQuantity()).isEqualTo(8);
            assertThat(stockB.getTotalQuantity()).isEqualTo(9);
        });
    }

    @Test
    @Order(3)
    void refundSuccess_restockInventoryAndReleasePromotion() {
        String refundId = "refund-" + UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("refundId", refundId);
        payload.put("paymentIntentId", paymentIntentId);
        payload.put("tradeId", tradeId);
        payload.put("refundAmountCents", payableAmountCents);
        payload.put("traceId", "trace-refund-" + tradeId);

        kafkaTestProducer.sendEvent("REFUND_REQUESTED", payload);

        TradeJpaRepository tradeRepository = getOrderBean(TradeJpaRepository.class);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            TradeEntity trade = tradeRepository.findByTradeId(tradeId).orElseThrow();
            assertThat(trade.getPayStatus()).isEqualTo("REFUNDED");
        });

        JpaInventoryStockRepository stockRepository = getInventoryBean(JpaInventoryStockRepository.class);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            InventoryStockEntity stockA = stockRepository.findByShopIdAndSkuId(SHOP_A, SKU_A).orElseThrow();
            InventoryStockEntity stockB = stockRepository.findByShopIdAndSkuId(SHOP_B, SKU_B).orElseThrow();
            assertThat(stockA.getTotalQuantity()).isEqualTo(10);
            assertThat(stockB.getTotalQuantity()).isEqualTo(10);
        });

        JpaInventoryAdjustmentRepository adjustmentRepository = getInventoryBean(JpaInventoryAdjustmentRepository.class);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(adjustmentRepository.existsByReasonAndReferenceId(
                StockAppService.ADJUST_REASON_RESTOCK_REFUND, refundId)).isTrue();
        });
    }

    private void seedInventory() {
        JpaInventoryStockRepository stockRepository = getInventoryBean(JpaInventoryStockRepository.class);
        JpaInventoryReservationRepository reservationRepository = getInventoryBean(JpaInventoryReservationRepository.class);
        JpaInventoryAdjustmentRepository adjustmentRepository = getInventoryBean(JpaInventoryAdjustmentRepository.class);

        adjustmentRepository.deleteAll();
        reservationRepository.deleteAll();
        stockRepository.deleteAll();

        stockRepository.save(new InventoryStockEntity()
            .setShopId(SHOP_A)
            .setSkuId(SKU_A)
            .setTotalQuantity(10)
            .setReservedQuantity(0));
        stockRepository.save(new InventoryStockEntity()
            .setShopId(SHOP_B)
            .setSkuId(SKU_B)
            .setTotalQuantity(10)
            .setReservedQuantity(0));
    }

    private void cleanupOrderAndPayment() {
        OrderLineJpaRepository orderLineJpaRepository = getOrderBean(OrderLineJpaRepository.class);
        ShopOrderJpaRepository shopOrderJpaRepository = getOrderBean(ShopOrderJpaRepository.class);
        TradeJpaRepository tradeRepository = getOrderBean(TradeJpaRepository.class);

        orderLineJpaRepository.deleteAll();
        shopOrderJpaRepository.deleteAll();
        tradeRepository.deleteAll();

        PaymentOrderJpaRepository paymentOrderRepository = getPaymentBean(PaymentOrderJpaRepository.class);
        RefundRecordJpaRepository refundRecordRepository = getPaymentBean(RefundRecordJpaRepository.class);
        refundRecordRepository.deleteAll();
        paymentOrderRepository.deleteAll();
    }

    private Map<String, Object> line(String skuId, String productId, String productName,
                                     String shopId, String sellerId, int quantity,
                                     long priceCents, long weightGrams) {
        Map<String, Object> line = new HashMap<>();
        line.put("skuId", skuId);
        line.put("productId", productId);
        line.put("productName", productName);
        line.put("shopId", shopId);
        line.put("sellerId", sellerId);
        line.put("quantity", quantity);
        line.put("priceCents", priceCents);
        line.put("weightGrams", weightGrams);
        return line;
    }

    @Test
    @Order(4)
    void createTradeWithMultipleCoupons_shouldStoreCouponCodes() {
        seedPromotionCoupons();
        cleanupOrderAndPayment();

        String newTradeId = UUID.randomUUID().toString();
        String newBuyerId = "buyer-multi-coupon-" + UUID.randomUUID();

        Map<String, Object> request = new HashMap<>();
        request.put("tradeId", newTradeId);
        request.put("buyerId", newBuyerId);
        request.put("buyerNick", "buyer-nick");
        request.put("addressId", "addr-002");
        request.put("traceId", "trace-multi-" + newTradeId);

        List<String> platformCoupons = List.of("C202602", "C202603");
        Map<String, List<String>> shopCoupons = new HashMap<>();
        shopCoupons.put(SHOP_A, List.of("P8888"));
        shopCoupons.put(SHOP_B, List.of("S1111", "S2222"));

        request.put("platformCouponCodes", platformCoupons);
        request.put("shopCouponCodesByShop", shopCoupons);

        List<Map<String, Object>> orderLines = new ArrayList<>();
        orderLines.add(line(SKU_A, "prod-1", "Product A", SHOP_A, "seller-A", 1, 3000L, 0L));
        orderLines.add(line(SKU_B, "prod-2", "Product B", SHOP_B, "seller-B", 1, 2000L, 0L));
        request.put("orderLines", orderLines);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Idempotency-Key", "idem-multi-" + newTradeId);
        ResponseEntity<Map> response = restTemplate.exchange(
            orderBaseUrl + "/order/trades",
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            Map.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        TradeJpaRepository tradeRepository = getOrderBean(TradeJpaRepository.class);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            TradeEntity trade = tradeRepository.findByTradeId(newTradeId).orElseThrow();
            assertThat(trade.getCouponCodes()).isNotNull();
            assertThat(trade.getCouponCodes()).contains("C202602", "C202603", "P8888", "S1111", "S2222");
        });
    }

    private void seedPromotionCoupons() {
        JpaCouponRepository couponRepository = getPromotionBean(JpaCouponRepository.class);
        couponRepository.deleteAll();

        CouponEntity platformCoupon1 = new CouponEntity();
        platformCoupon1.setId(UUID.randomUUID());
        platformCoupon1.setCouponNo("C202602");
        platformCoupon1.setCouponType("FULL_REDUCTION");
        platformCoupon1.setScopeType("PLATFORM");
        platformCoupon1.setShopId(null);
        couponRepository.save(platformCoupon1);

        CouponEntity platformCoupon2 = new CouponEntity();
        platformCoupon2.setId(UUID.randomUUID());
        platformCoupon2.setCouponNo("C202603");
        platformCoupon2.setCouponType("DISCOUNT");
        platformCoupon2.setScopeType("PLATFORM");
        platformCoupon2.setShopId(null);
        couponRepository.save(platformCoupon2);

        CouponEntity shopCouponA = new CouponEntity();
        shopCouponA.setId(UUID.randomUUID());
        shopCouponA.setCouponNo("P8888");
        shopCouponA.setCouponType("FULL_REDUCTION");
        shopCouponA.setScopeType("STORE");
        shopCouponA.setShopId(SHOP_A);
        couponRepository.save(shopCouponA);

        CouponEntity shopCouponB1 = new CouponEntity();
        shopCouponB1.setId(UUID.randomUUID());
        shopCouponB1.setCouponNo("S1111");
        shopCouponB1.setCouponType("DISCOUNT");
        shopCouponB1.setScopeType("STORE");
        shopCouponB1.setShopId(SHOP_B);
        couponRepository.save(shopCouponB1);

        CouponEntity shopCouponB2 = new CouponEntity();
        shopCouponB2.setId(UUID.randomUUID());
        shopCouponB2.setCouponNo("S2222");
        shopCouponB2.setCouponType("FULL_REDUCTION");
        shopCouponB2.setScopeType("STORE");
        shopCouponB2.setShopId(SHOP_B);
        couponRepository.save(shopCouponB2);
    }
}
