package com.github.spud.tinystore.order.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.application.command.merchant.ApproveCancelOrderCommand;
import com.github.spud.tinystore.order.application.command.merchant.MerchantAcceptCommand;
import com.github.spud.tinystore.order.application.command.merchant.RejectCancelOrderCommand;
import com.github.spud.tinystore.order.application.command.merchant.ShipOrderCommand;
import com.github.spud.tinystore.order.application.service.OrderApplicationService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@ImportAutoConfiguration(exclude = {org.springframework.cloud.openfeign.FeignAutoConfiguration.class})
@WebMvcTest(MerchantOrderController.class)
@ContextConfiguration(classes = {MerchantOrderController.class, MerchantOrderControllerTest.Config.class})
@AutoConfigureMockMvc(addFilters = false)
class MerchantOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderApplicationService applicationService;

    @TestConfiguration
    static class Config {
        @Bean
        OrderApplicationService applicationService() {
            return Mockito.mock(OrderApplicationService.class);
        }
    }

    @Test
    @DisplayName("商家接单：200 且调用应用服务")
    void receiveOrder_ok() throws Exception {
        doNothing().when(applicationService).merchantAccept(any(MerchantAcceptCommand.class));

        String body = objectMapper.writeValueAsString(Map.of(
            "orderId", "ORDER-M-1",
            "operatorId", "OP-1",
            "idempotencyKey", "ACCEPT#ORDER-M-1#OP-1"
        ));

        mockMvc.perform(post("/order/merchant/order/receive")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.status").value("success"));
    }

    @Test
    @DisplayName("商家同意取消：200 返回成功ACK")
    void cancelApprove_ok() throws Exception {
        doNothing().when(applicationService).approveCancelRequest(any(ApproveCancelOrderCommand.class));

        String body = objectMapper.writeValueAsString(Map.of(
            "orderId", "ORDER-M-2",
            "operatorId", "OP-2",
            "remark", "ok",
            "idempotencyKey", "CANCEL-APPROVE#ORDER-M-2#OP-2"
        ));

        mockMvc.perform(post("/order/merchant/cancel/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.status").value("success"));
    }

    @Test
    @DisplayName("商家拒绝取消：200 返回成功ACK")
    void cancelReject_ok() throws Exception {
        doNothing().when(applicationService).rejectCancelRequest(any(RejectCancelOrderCommand.class));

        String body = objectMapper.writeValueAsString(Map.of(
            "orderId", "ORDER-M-3",
            "operatorId", "OP-3",
            "reasonCode", "NOT_ALLOWED",
            "remark", "no",
            "idempotencyKey", "CANCEL-REJECT#ORDER-M-3#OP-3"
        ));

        mockMvc.perform(post("/order/merchant/cancel/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.status").value("success"));
    }

    @Test
    @DisplayName("商家发货：200 且调用应用服务")
    void shipOrder_ok() throws Exception {
        doNothing().when(applicationService).shipOrder(any(ShipOrderCommand.class));

        String body = objectMapper.writeValueAsString(Map.of(
            "orderId", "ORDER-M-4",
            "operatorId", "OP-4",
            "logistics", Map.of(
                "companyCode", "SF",
                "trackingNo", "SF1234567890",
                "companyName", "ShunFeng"
            ),
            "items", List.of(Map.of("skuId", "SKU-1", "quantity", 1)),
            "idempotencyKey", "SHIP#ORDER-M-4#PKG-1"
        ));

        mockMvc.perform(post("/order/merchant/ship")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.status").value("success"));
    }

    @Test
    @DisplayName("商家确认妥投：200 返回成功ACK")
    void deliveryConfirm_ok() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "orderId", "ORDER-M-5",
            "trackingNo", "SF123",
            "deliveredAt", System.currentTimeMillis(),
            "eventId", UUID.randomUUID().toString(),
            "source", "MERCHANT"
        ));

        mockMvc.perform(post("/order/merchant/delivery/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.status").value("success"));
    }

    @Test
    @DisplayName("商家确认妥投：重复提交两次均返回200（幂等回放）")
    void deliveryConfirm_replay_ok_twice() throws Exception {
        String idempotentEvent = java.util.UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(Map.of(
            "orderId", "ORDER-M-REPLAY-5",
            "trackingNo", "SF-RE-M",
            "deliveredAt", System.currentTimeMillis(),
            "eventId", idempotentEvent,
            "source", "MERCHANT"
        ));

        mockMvc.perform(post("/order/merchant/delivery/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());
        mockMvc.perform(post("/order/merchant/delivery/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());
    }
}
