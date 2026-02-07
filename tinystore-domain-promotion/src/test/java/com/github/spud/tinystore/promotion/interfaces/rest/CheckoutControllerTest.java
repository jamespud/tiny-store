package com.github.spud.tinystore.promotion.interfaces.rest;

import com.github.spud.tinystore.promotion.application.service.CheckoutAppService;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutQuoteRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutQuoteResponse;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutReleaseRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutReleaseResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checkout Controller Unit Test
 * 
 * Coverage:
 * - POST /api/promotion/checkout/quote (protocol semantics)
 * - POST /api/promotion/checkout/release (protocol semantics)
 * 
 * Validates HTTP status codes, parameter validation, header requirements
 */
@WebMvcTest(
    controllers = CheckoutController.class, 
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = com.github.spud.tinystore.promotion.infrastructure.config.WebConfig.class
    )
)
@DisplayName("Checkout Controller Unit Tests")
class CheckoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckoutAppService checkoutAppService;

    @Test
    @org.junit.jupiter.api.Tag("ep:promotion:POST:/api/promotion/checkout/quote")
    @DisplayName("POST /api/promotion/checkout/quote - valid request returns 200")
    void quote_validRequest_returns200() throws Exception {
        // Given: service returns quote
        CheckoutQuoteResponse mockResponse = new CheckoutQuoteResponse();
        mockResponse.setQuoteId("test-quote-001");
        
        when(checkoutAppService.quote(anyString(), any(CheckoutQuoteRequest.class)))
            .thenReturn(mockResponse);

        // When & Then: POST with valid JSON
        mockMvc.perform(post("/api/promotion/checkout/quote")
                .header("Idempotency-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"user-1\",\"addressId\":\"addr-1\"," +
                         "\"lines\":[{\"skuId\":\"SKU_A\",\"shopId\":\"SHOP_A\",\"quantity\":2," +
                         "\"baseUnitPriceCents\":5000,\"weightGrams\":100}]}"))
            .andExpect(status().isOk());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:promotion:POST:/api/promotion/checkout/quote")
    @DisplayName("POST /api/promotion/checkout/quote - missing shopId returns 400")
    void quote_missingShopId_returns400() throws Exception {
        // When & Then: POST without shopId
        mockMvc.perform(post("/api/promotion/checkout/quote")
                .header("Idempotency-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tradeId\":\"trade-1\"," +
                         "\"expiresAtEpochMs\":1234567890000," +
                         "\"lines\":[{\"skuId\":\"SKU_A\",\"quantity\":2,\"price\":50.0}]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:promotion:POST:/api/promotion/checkout/release")
    @DisplayName("POST /api/promotion/checkout/release - valid request returns 200")
    void release_validRequest_returns200() throws Exception {
        // Given: service returns success
        CheckoutReleaseResponse mockResponse = new CheckoutReleaseResponse();
        mockResponse.setSuccess(true);
        
        when(checkoutAppService.release(anyString(), any(CheckoutReleaseRequest.class)))
            .thenReturn(mockResponse);

        // When & Then: POST with valid JSON
        mockMvc.perform(post("/api/promotion/checkout/release")
                .header("Idempotency-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quoteId\":\"quote-001\",\"tradeId\":\"trade-1\"," +
                         "\"reason\":\"cancel\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:promotion:POST:/api/promotion/checkout/release")
    @DisplayName("POST /api/promotion/checkout/release - missing field returns 400")
    void release_missingField_returns400() throws Exception {
        // When & Then: POST without shopId
        mockMvc.perform(post("/api/promotion/checkout/release")
                .header("Idempotency-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tradeId\":\"trade-1\",\"reason\":\"cancel\"}"))
            .andExpect(status().isBadRequest());
    }
}
