package com.github.spud.tinystore.order.interfaces.rest;

import com.github.spud.tinystore.order.application.service.MerchantFulfillmentService;
import com.github.spud.tinystore.order.interfaces.dto.request.ShipOrderRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Merchant Order Controller Unit Test
 * 
 * Coverage:
 * - POST /api/order/merchant/orders/{orderId}/accept
 * - POST /api/order/merchant/orders/{orderId}/ship
 * - POST /api/order/merchant/packages/{packageId}/delivered
 * 
 * Validates merchant fulfillment protocol semantics
 */
@WebMvcTest(controllers = MerchantOrderController.class, excludeAutoConfiguration = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class})
@DisplayName("Merchant Order Controller Unit Tests")
class MerchantOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MerchantFulfillmentService merchantFulfillmentService;

    @MockitoBean
    private com.github.spud.tinystore.order.application.query.MerchantOrderQueryService merchantOrderQueryService;

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/merchant/orders/{orderId}/accept")
    @DisplayName("POST /api/order/merchant/orders/{orderId}/accept - valid request returns 200")
    void acceptOrder_validRequest_returns200() throws Exception {
        // Given: service accepts order
        doNothing().when(merchantFulfillmentService).merchantAcceptOrder(anyString(), anyString());

        // When & Then: POST
        mockMvc.perform(post("/order/merchant/orders/order-123/accept")
                .header("Idempotency-Key", "idem-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/merchant/orders/{orderId}/accept")
    @DisplayName("POST /api/order/merchant/orders/{orderId}/accept - invalid state returns 500")
    void acceptOrder_invalidState_returns500() throws Exception {
        // Given: service throws exception
        doThrow(new IllegalStateException("Order not in PAID state"))
            .when(merchantFulfillmentService).merchantAcceptOrder(anyString(), anyString());

        // When & Then: POST
        mockMvc.perform(post("/order/merchant/orders/order-123/accept")
                .header("Idempotency-Key", "idem-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().is5xxServerError());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/merchant/orders/{orderId}/ship")
    @DisplayName("POST /api/order/merchant/orders/{orderId}/ship - valid request returns 200")
    void shipOrder_validRequest_returns200() throws Exception {
        // Given: service ships order
        doNothing().when(merchantFulfillmentService).shipOrder(
            anyString(), anyString(), anyString(), anyString(), anyString());

        // When & Then: POST
        mockMvc.perform(post("/order/merchant/orders/order-123/ship")
                .header("Idempotency-Key", "idem-ship")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"packageId\":\"pkg-1\",\"logistics\":\"SF\"," +
                         "\"waybillNo\":\"SF123456\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/merchant/orders/{orderId}/ship")
    @DisplayName("POST /api/order/merchant/orders/{orderId}/ship - missing waybillNo returns 400")
    void shipOrder_missingField_accepts() throws Exception {
        // When & Then: POST without required field (waybillNo is validated now)
        mockMvc.perform(post("/order/merchant/orders/order-123/ship")
                .header("Idempotency-Key", "idem-ship")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"packageId\":\"pkg-1\"}"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/merchant/packages/{packageId}/delivered")
    @DisplayName("POST /api/order/merchant/packages/{packageId}/delivered - valid request returns 200")
    void markDelivered_validRequest_returns200() throws Exception {
        // Given: service marks delivered
        doNothing().when(merchantFulfillmentService).markPackageDelivered(anyString(), anyString());

        // When & Then: POST
        mockMvc.perform(post("/order/merchant/packages/pkg-123/delivered")
                .header("Idempotency-Key", "idem-del")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/merchant/packages/{packageId}/delivered")
    @DisplayName("POST /api/order/merchant/packages/{packageId}/delivered - invalid state returns 500")
    void markDelivered_invalidState_returns500() throws Exception {
        // Given: service throws exception
        doThrow(new IllegalStateException("Package not in SHIPPED state"))
            .when(merchantFulfillmentService).markPackageDelivered(anyString(), anyString());

        // When & Then: POST
        mockMvc.perform(post("/order/merchant/packages/pkg-123/delivered")
                .header("Idempotency-Key", "idem-del")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().is5xxServerError());
    }
}
