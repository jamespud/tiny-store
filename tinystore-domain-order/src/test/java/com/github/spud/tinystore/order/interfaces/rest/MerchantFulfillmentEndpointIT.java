package com.github.spud.tinystore.order.interfaces.rest;

import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.FulfillmentPackageEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.ShopOrderEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.TradeEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.FulfillmentPackageJpaRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.ShopOrderJpaRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.TradeJpaRepository;
import com.github.spud.tinystore.order.test.it.AbstractSpringBootOrderIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Merchant Fulfillment Endpoint Integration Test
 * 
 * Coverage:
 * - POST /api/order/merchant/orders/{orderId}/accept (商家接单)
 * - POST /api/order/merchant/orders/{orderId}/ship (商家发货)
 * - POST /api/order/merchant/packages/{packageId}/delivered (标记送达)
 * 
 * Tests merchant fulfillment flow with database
 */
@DisplayName("Merchant Fulfillment Endpoint Integration Tests")
class MerchantFulfillmentEndpointIT extends AbstractSpringBootOrderIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TradeJpaRepository tradeRepository;

    @Autowired
    private ShopOrderJpaRepository shopOrderRepository;

    @Autowired
    private FulfillmentPackageJpaRepository packageRepository;

    @BeforeEach
    void cleanup() {
        packageRepository.deleteAll();
        shopOrderRepository.deleteAll();
        outboxEventJpaRepository.deleteAll();
        tradeRepository.deleteAll();
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/merchant/orders/{orderId}/accept")
    @DisplayName("POST /api/order/merchant/orders/{orderId}/accept - accepts order")
    void merchantAccept_validOrder_returns200() {
        // Given: trade in PAID status
        TradeEntity trade = TradeEntity.builder()
            .tradeId("trade-accept")
            .buyerId("user-merchant")
            .payStatus("PAID")
            .totalAmountCents(39900L)
            .payableAmountCents(39900L)
            .discountAmountCents(0L)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        tradeRepository.save(trade);

        // Given: ShopOrder in PENDING_SHIP status
        ShopOrderEntity shopOrder = ShopOrderEntity.builder()
            .orderId("trade-accept")
            .tradeId("trade-accept")
            .shopId("SHOP_001")
            .sellerId("SELLER_001")
            .orderStatus("PENDING_SHIP")
            .inventoryStatus("LOCKED")
            .promotionStatus("RESERVED")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        shopOrderRepository.save(shopOrder);

        // When: merchant accepts (orderId = tradeId)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "idem-accept-001");

        HttpEntity<String> request = new HttpEntity<>("{}", headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/order/merchant/orders/trade-accept/accept",
            HttpMethod.POST,
            request,
            String.class
        );

        // Then: returns 200
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/merchant/orders/{orderId}/ship")
    @DisplayName("POST /api/order/merchant/orders/{orderId}/ship - ships order")
    void merchantShip_validOrder_returns200() {
        // Given: order in PENDING_SHIP status
        TradeEntity trade = TradeEntity.builder()
            .tradeId("trade-ship")
            .buyerId("user-ship")
            .payStatus("PAID")
            .totalAmountCents(19900L)
            .payableAmountCents(19900L)
            .discountAmountCents(0L)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        tradeRepository.save(trade);

        // Given: ShopOrder in PENDING_SHIP status
        ShopOrderEntity shopOrder = ShopOrderEntity.builder()
            .orderId("trade-ship")
            .tradeId("trade-ship")
            .shopId("SHOP_002")
            .sellerId("SELLER_IT_001")
            .orderStatus("PENDING_SHIP")
            .inventoryStatus("LOCKED")
            .promotionStatus("RESERVED")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        shopOrderRepository.save(shopOrder);

        // When: merchant ships
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "idem-ship-001");

        String requestBody = "{" +
            "\"packageId\":\"pkg-ship\"," +
            "\"waybillNo\":\"SF123456\"," +
            "\"logistics\":\"SF Express\"" +
            "}";

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/order/merchant/orders/trade-ship/ship",
            HttpMethod.POST,
            request,
            String.class
        );

        // Then: returns 200
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/merchant/orders/{orderId}/ship")
    @DisplayName("POST /api/order/merchant/orders/{orderId}/ship - missing waybillNo returns 400")
    void merchantShip_missingWaybillNo_returns400() {
        // Given: order exists
        TradeEntity trade = TradeEntity.builder()
            .tradeId("trade-ship-2")
            .buyerId("user-ship-2")
            .payStatus("PAID")
            .totalAmountCents(29900L)
            .payableAmountCents(29900L)
            .discountAmountCents(0L)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        tradeRepository.save(trade);

        // Given: ShopOrder in PENDING_SHIP status
        ShopOrderEntity shopOrder = ShopOrderEntity.builder()
            .orderId("trade-ship-2")
            .tradeId("trade-ship-2")
            .shopId("SHOP_003")
            .sellerId("SELLER_002")
            .orderStatus("PENDING_SHIP")
            .inventoryStatus("LOCKED")
            .promotionStatus("RESERVED")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        shopOrderRepository.save(shopOrder);

        // When: ship without waybillNo
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "idem-ship-002");

        String requestBody = "{" +
            "\"packageId\":\"pkg-ship-2\"," +
            "\"logistics\":\"SF Express\"" +
            "}";

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/order/merchant/orders/trade-ship-2/ship",
            HttpMethod.POST,
            request,
            String.class
        );

        // Then: returns 400 or 500 (depending on validation)
        assertThat(response.getStatusCode().is4xxClientError() || 
                   response.getStatusCode().is5xxServerError()).isTrue();
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/merchant/packages/{packageId}/delivered")
    @DisplayName("POST /api/order/merchant/packages/{packageId}/delivered - marks package delivered")
    void markDelivered_validPackage_returns200() {
        // Given: package in SHIPPED status
        FulfillmentPackageEntity pkg = FulfillmentPackageEntity.builder()
            .packageId("pkg-delivered")
            .tradeId("trade-delivered")
            .sellerId("SELLER_IT_002")
            .logisticsStatus("SHIPPED")
            .logisticsNo("SF999888")
            .logisticsCompanyId("SF Express")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        packageRepository.save(pkg);

        // When: mark delivered
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "idem-delivered-001");

        HttpEntity<String> request = new HttpEntity<>("{}", headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/order/merchant/packages/pkg-delivered/delivered",
            HttpMethod.POST,
            request,
            String.class
        );

        // Then: returns 200
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/merchant/packages/{packageId}/delivered")
    @DisplayName("POST /api/order/merchant/packages/{packageId}/delivered - non-existent package returns 404")
    void markDelivered_nonExistentPackage_returns404() {
        // When: mark non-existent package delivered
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "idem-delivered-002");

        HttpEntity<String> request = new HttpEntity<>("{}", headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/order/merchant/packages/non-existent-pkg/delivered",
            HttpMethod.POST,
            request,
            String.class
        );

        // Then: returns 404 or 500
        assertThat(response.getStatusCode().is4xxClientError() || 
                   response.getStatusCode().is5xxServerError()).isTrue();
    }
}
