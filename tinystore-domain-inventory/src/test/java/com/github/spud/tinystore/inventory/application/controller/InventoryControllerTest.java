package com.github.spud.tinystore.inventory.application.controller;

import com.github.spud.tinystore.inventory.domain.command.*;
import com.github.spud.tinystore.inventory.domain.model.Reservation;
import com.github.spud.tinystore.inventory.domain.model.ReservationState;
import com.github.spud.tinystore.inventory.domain.service.InventoryDomainService;
import com.github.spud.tinystore.inventory.interfaces.rest.InventoryController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = InventoryController.class)
class InventoryControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    InventoryDomainService service;

    @Test
    @DisplayName("reserve 正常返回")
    void reserveOk() throws Exception {
        Reservation reservation = new Reservation(UUID.randomUUID().toString(), "s1", "sku1", 5, ReservationState.PENDING, Instant.now().plusSeconds(60), "op1", Instant.now(), Instant.now(), 0L);
        when(service.reserve(ArgumentMatchers.any(ReserveCommand.class))).thenReturn(reservation);
        mockMvc.perform(post("/api/inventory/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shopId\":\"s1\",\"skuId\":\"sku1\",\"quantity\":5,\"expireSeconds\":60,\"operationId\":\"op1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.reservationId").value(reservation.getReservationId()));
    }

    @Test
    @DisplayName("available 查询")
    void availableOk() throws Exception {
        when(service.available("s1","sku1")).thenReturn(42L);
        mockMvc.perform(get("/api/inventory/stock/available")
                        .param("shopId","s1")
                        .param("skuId","sku1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(42));
    }
}

