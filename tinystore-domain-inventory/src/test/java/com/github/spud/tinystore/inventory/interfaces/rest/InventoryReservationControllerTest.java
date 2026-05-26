package com.github.spud.tinystore.inventory.interfaces.rest;

import com.github.spud.tinystore.inventory.application.service.InventoryReservationAppService;
import com.github.spud.tinystore.inventory.interfaces.dto.DeductResponse;
import com.github.spud.tinystore.inventory.interfaces.dto.InventoryConfirmResponse;
import com.github.spud.tinystore.inventory.interfaces.dto.InventoryReleaseResponse;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = InventoryReservationController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = com.github.spud.tinystore.inventory.infrastructure.config.WebConfig.class
    )
)
@DisplayName("InventoryReservationController Unit Tests")
class InventoryReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryReservationAppService appService;

    @Test
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/reservations/reserve")
    @DisplayName("POST /api/inventory/reservations/reserve - valid request returns 200")
    void reserve_validRequest_returns200() throws Exception {
        DeductResponse response = DeductResponse.ok(List.of(
            DeductResponse.OccupyPairDto.builder()
                .shopId("SHOP_A")
                .skuId("SKU_A")
                .occupyId("res-001")
                .build()
        ));
        when(appService.reserve(anyString(), any())).thenReturn(response);

        mockMvc.perform(post("/api/inventory/reservations/reserve")
                .header("Idempotency-Key", "reserve-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tradeId\":\"trade-001\",\"orderId\":\"order-001\"," +
                         "\"items\":[{\"shopId\":\"SHOP_A\",\"skuId\":\"SKU_A\",\"quantity\":1}]}"))
            .andExpect(status().isOk());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/reservations/reserve")
    @DisplayName("POST /api/inventory/reservations/reserve - missing tradeId returns 400")
    void reserve_missingTradeId_returns400() throws Exception {
        mockMvc.perform(post("/api/inventory/reservations/reserve")
                .header("Idempotency-Key", "reserve-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"order-001\"," +
                         "\"items\":[{\"shopId\":\"SHOP_A\",\"skuId\":\"SKU_A\",\"quantity\":1}]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/reservations/confirm")
    @DisplayName("POST /api/inventory/reservations/confirm - valid request returns 200")
    void confirm_validRequest_returns200() throws Exception {
        InventoryConfirmResponse response = InventoryConfirmResponse.ok(List.of(
            InventoryConfirmResponse.ReservationRefDto.builder()
                .shopId("SHOP_A")
                .skuId("SKU_A")
                .reservationId("res-001")
                .build()
        ));
        when(appService.confirm(anyString(), any())).thenReturn(response);

        mockMvc.perform(post("/api/inventory/reservations/confirm")
                .header("Idempotency-Key", "confirm-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentId\":\"pay-001\",\"tradeId\":\"trade-001\",\"orderId\":\"order-001\"," +
                         "\"occupyPairs\":[{\"shopId\":\"SHOP_A\",\"skuId\":\"SKU_A\",\"occupyId\":\"res-001\"}]}"))
            .andExpect(status().isOk());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/reservations/release")
    @DisplayName("POST /api/inventory/reservations/release - valid request returns 200")
    void release_validRequest_returns200() throws Exception {
        when(appService.release(anyString(), any())).thenReturn(InventoryReleaseResponse.ok("released"));

        mockMvc.perform(post("/api/inventory/reservations/release")
                .header("Idempotency-Key", "release-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"order-001\",\"reason\":\"cancel\"," +
                         "\"occupyPairs\":[{\"shopId\":\"SHOP_A\",\"skuId\":\"SKU_A\",\"occupyId\":\"res-001\"}]}"))
            .andExpect(status().isOk());
    }
}