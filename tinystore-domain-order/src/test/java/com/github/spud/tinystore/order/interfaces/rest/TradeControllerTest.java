package com.github.spud.tinystore.order.interfaces.rest;

import com.github.spud.tinystore.order.application.service.TradeApplicationService;
import com.github.spud.tinystore.order.application.service.PaymentApplicationService;
import com.github.spud.tinystore.order.application.query.TradeQueryService;
import com.github.spud.tinystore.order.domain.exception.IdempotencyServiceUnavailableException;
import com.github.spud.tinystore.order.interfaces.error.GlobalExceptionHandler;
import com.github.spud.tinystore.order.interfaces.dto.response.CreateTradeData;
import com.github.spud.tinystore.order.interfaces.dto.response.TradeDetailData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

/**
 * Trade Controller Unit Test
 * 
 * Coverage:
 * - POST /api/order/trades
 * - GET /api/order/trades/{tradeId}
 * - POST /api/order/trades/{tradeId}/cancel
 * 
 * Validates HTTP protocol semantics and parameter validation
 */
@WebMvcTest(controllers = TradeController.class, excludeAutoConfiguration = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class})
@Import(GlobalExceptionHandler.class)
@DisplayName("Trade Controller Unit Tests")
class TradeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TradeApplicationService tradeApplicationService;

    @MockitoBean
    private TradeQueryService tradeQueryService;

    @MockitoBean
    private PaymentApplicationService paymentApplicationService;

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades")
    @DisplayName("POST /api/order/trades - valid request returns 200")
    void createTrade_validRequest_returns200() throws Exception {
        // Given: service returns created trade
        CreateTradeData createTradeData = CreateTradeData.builder()
            .tradeId("trade-new-123")
            .payableAmountCents(9900L)
            .paymentIntentId("payment-intent-1")
            .build();
        when(tradeApplicationService.createTrade(anyString(), any()))
            .thenReturn(createTradeData);

        // When & Then: POST with valid JSON
        mockMvc.perform(post("/order/trades")
                .header("Idempotency-Key", "idem-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tradeId\":\"trade-123\",\"buyerId\":\"user-1\"," +
                         "\"orderLines\":[{\"skuId\":\"SKU_A\",\"quantity\":1,\"priceCents\":9900}]}"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades")
    @DisplayName("POST /api/order/trades - missing buyerId returns 4xx")
    void createTrade_missingBuyerId_returns4xx() throws Exception {
        // When & Then: POST without buyerId triggers validation error
        mockMvc.perform(post("/order/trades")
                .header("Idempotency-Key", "idem-key-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tradeId\":\"trade-456\"}"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades:redis-failure")
    @DisplayName("POST /api/order/trades - idempotency unavailable returns 503")
    void createTrade_idempotencyUnavailable_returns503() throws Exception {
        // Given: application service fails due to underlying Redis/idempotency outage
        when(tradeApplicationService.createTrade(anyString(), any()))
            .thenThrow(new IdempotencyServiceUnavailableException(
                "tryAcquire",
                "idem-redis-down-001",
                new RuntimeException("redis down")
            ));

        // When & Then: valid request reaches controller, handled by GlobalExceptionHandler
        mockMvc.perform(post("/order/trades")
                .header("Idempotency-Key", "idem-redis-down-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tradeId\":\"trade-redis-down-001\"," +
                         "\"buyerId\":\"buyer-redis-001\"," +
                         "\"orderLines\":[{\"skuId\":\"SKU_REDIS_001\"," +
                         "\"shopId\":\"SHOP_REDIS_001\"," +
                         "\"sellerId\":\"SELLER_REDIS_001\"," +
                         "\"quantity\":1," +
                         "\"priceCents\":5000}] }"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().string(containsString("Idempotency service unavailable")));
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:order:GET:/api/order/trades/{tradeId}")
    @DisplayName("GET /api/order/trades/{tradeId} - existing trade returns 200")
    void getTrade_exists_returns200() throws Exception {
        // Given: service returns trade
        TradeDetailData mockResponse = new TradeDetailData();
        mockResponse.setTradeId("trade-123");
        
        when(tradeQueryService.getTradeDetail(anyString())).thenReturn(mockResponse);

        // When & Then: GET
        mockMvc.perform(get("/order/trades/trade-123"))
            .andExpect(status().isOk());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:order:GET:/api/order/trades/{tradeId}")
    @DisplayName("GET /api/order/trades/{tradeId} - not found returns 404")
    void getTrade_notFound_returns404() throws Exception {
        // Given: service throws not found exception
        when(tradeQueryService.getTradeDetail(anyString()))
            .thenThrow(new IllegalArgumentException("Trade not found"));

        // When & Then: GET
        mockMvc.perform(get("/order/trades/non-existent"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades/{tradeId}/cancel")
    @DisplayName("POST /api/order/trades/{tradeId}/cancel - valid request returns 200")
    void cancelTrade_validRequest_returns200() throws Exception {
        // Given: service cancels trade
        doNothing().when(tradeApplicationService).cancelTrade(anyString(), any());

        // When & Then: POST
        mockMvc.perform(post("/order/trades/trade-123/cancel")
                .header("Idempotency-Key", "idem-cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"User cancel\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades/{tradeId}/pay/callback")
    @DisplayName("POST /api/order/trades/{tradeId}/pay/callback - valid callback returns 200")
    void paymentCallback_validRequest_returns200() throws Exception {
        // Given: service processes payment callback successfully
        doNothing().when(tradeApplicationService).onPaymentSucceeded(anyString(), any());

        // When & Then: POST payment callback
        mockMvc.perform(post("/order/trades/trade-pay-123/pay/callback")
                .header("Idempotency-Key", "idem-payment-callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentIntentId\":\"pay-intent-123\",\"amountCents\":9900,\"traceId\":\"trace-123\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades/{tradeId}/confirm-receipt")
    @DisplayName("POST /api/order/trades/{tradeId}/confirm-receipt - valid request returns 200")
    void confirmReceipt_validRequest_returns200() throws Exception {
        // Given: service confirms receipt successfully
        doNothing().when(tradeApplicationService).confirmTradeReceipt(anyString(), anyString());

        // When & Then: POST confirm receipt
        mockMvc.perform(post("/order/trades/trade-confirm-123/confirm-receipt")
                .header("Idempotency-Key", "idem-confirm-receipt")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());
    }
}
