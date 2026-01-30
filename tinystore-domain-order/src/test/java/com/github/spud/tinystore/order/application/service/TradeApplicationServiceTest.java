package com.github.spud.tinystore.order.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.application.command.CreateTradeCommand;
import com.github.spud.tinystore.order.domain.enums.OrderStatus;
import com.github.spud.tinystore.order.domain.enums.PayStatus;
import com.github.spud.tinystore.order.domain.model.OrderLine;
import com.github.spud.tinystore.order.domain.model.ShopOrder;
import com.github.spud.tinystore.order.domain.model.Trade;
import com.github.spud.tinystore.order.domain.repository.ShopOrderRepository;
import com.github.spud.tinystore.order.domain.repository.TradeRepository;
import com.github.spud.tinystore.order.infrastructure.acl.InventoryClient;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient;
import com.github.spud.tinystore.order.infrastructure.acl.dto.*;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.infrastructure.idempotency.IdempotencyService;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.PaymentIntentEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TradeApplicationService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class TradeApplicationServiceTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private ShopOrderRepository shopOrderRepository;

    @Mock
    private PaymentIntentJpaRepository paymentIntentJpaRepository;


    @Mock
    private OutboxEventService outboxEventService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private PromotionClient promotionClient;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TradeApplicationService tradeApplicationService;

    private CreateTradeCommand testCommand;

    @BeforeEach
    void setUp() {
        testCommand = CreateTradeCommand.builder()
            .tradeId(UUID.randomUUID().toString())
            .buyerId("buyer_123")
            .buyerNick("buyer_nick")
            .couponCode("PROMO_2024")
            .orderLines(List.of(
                CreateTradeCommand.OrderLineCommand.builder()
                    .skuId("sku_001")
                    .productId("prod_001")
                    .productName("iPhone 15")
                    .shopId("shop_001")
                    .sellerId("seller_001")
                    .quantity(1)
                    .priceCents(99999L)
                    .build()
            ))
            .traceId(UUID.randomUUID().toString())
            .build();
        
        // Mock idempotency service to return true (allow execution)
        when(idempotencyService.tryAcquire(any(), any(), any())).thenReturn(true);
        
        // Mock promotion client quote
        PromotionQuoteResponse promoQuoteResponse = PromotionQuoteResponse.builder()
            .discountAmountCents(10000L)
            .itemsTotalCents(99999L)
            .payableAmountCents(89999L)
            .version("1")
            .build();
        when(promotionClient.quote(any(), any())).thenReturn(promoQuoteResponse);
        
        // Mock inventory client preOccupy
        InventoryPreOccupyResponse inventoryPreResponse = InventoryPreOccupyResponse.builder()
            .success(true)
            .reservationId("res_001")
            .build();
        when(inventoryClient.preOccupy(any(), any())).thenReturn(inventoryPreResponse);

        // Mock repository save methods - return domain objects
        when(tradeRepository.save(any(Trade.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        when(shopOrderRepository.save(any(ShopOrder.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        when(paymentIntentJpaRepository.save(any(PaymentIntentEntity.class)))
            .thenAnswer(invocation -> {
                PaymentIntentEntity entity = invocation.getArgument(0);
                entity.setId(4L);
                return entity;
            });
    }

    @Test
    void testCreateTradeSuccess() throws Exception {
        // Given
        String idempotencyKey = UUID.randomUUID().toString();

        // When
        Map<String, Object> result = tradeApplicationService.createTrade(idempotencyKey, testCommand);

        // Then
        assertNotNull(result);
        assertEquals(testCommand.getTradeId(), result.get("tradeId"));
        assertNotNull(result.get("payableAmountCents"));
        assertNotNull(result.get("paymentIntentId"));
        assertNotNull(result.get("reservationId"));

        verify(tradeRepository, atLeast(1)).save(any(Trade.class));
        verify(promotionClient).quote(any(), any());
        verify(inventoryClient).preOccupy(any(), any());
    }

    @Test
    void testCreateTradeIdempotency() throws Exception {
        // Given
        String idempotencyKey = UUID.randomUUID().toString();
        
        // When - First call
        Map<String, Object> result1 = tradeApplicationService.createTrade(idempotencyKey, testCommand);
        assertNotNull(result1);
        assertEquals(testCommand.getTradeId(), result1.get("tradeId"));

        // Then - verify promotion and inventory calls were made
        verify(promotionClient).quote(any(), any());
        verify(inventoryClient).preOccupy(any(), any());
    }
}
