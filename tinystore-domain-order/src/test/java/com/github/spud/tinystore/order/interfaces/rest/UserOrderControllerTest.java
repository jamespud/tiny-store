package com.github.spud.tinystore.order.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.application.command.user.AfterSaleApplyCommand;
import com.github.spud.tinystore.order.application.command.user.CancelOrderCommand;
import com.github.spud.tinystore.order.application.command.user.ConfirmReceiptCommand;
import com.github.spud.tinystore.order.application.command.user.ConfirmOrderCommand;
import com.github.spud.tinystore.order.application.command.user.SubmitOrderCommand;
import com.github.spud.tinystore.order.application.result.ConfirmOrderResult;
import com.github.spud.tinystore.order.application.result.ConfirmOrderResult.OrderSummary;
import com.github.spud.tinystore.order.application.result.SubmitOrderResult;
import com.github.spud.tinystore.order.domain.model.Money;
import com.github.spud.tinystore.order.application.service.OrderApplicationService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@ImportAutoConfiguration(exclude = {org.springframework.cloud.openfeign.FeignAutoConfiguration.class})
@WebMvcTest(UserOrderController.class)
@ContextConfiguration(classes = {UserOrderController.class, UserOrderControllerTest.Config.class})
@AutoConfigureMockMvc(addFilters = false)
class UserOrderControllerTest {

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
    @DisplayName("确认收货：200 且调用应用服务")
    void confirmReceipt_ok() throws Exception {
        // mock
        doNothing().when(applicationService).confirmReceipt(any(ConfirmReceiptCommand.class));

        String body = objectMapper.writeValueAsString(Map.of(
            "orderId", "ORDER-1",
            "idempotencyKey", "ORDER-1#U1"
        ));

        mockMvc.perform(post("/order/user/confirm-receipt")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-Id", "T1")
                .header("X-User-Id", "U1")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.status").value("success"));
    }

    @Test
    @DisplayName("确认收货重复调用：仍返回200 (幂等回放占位)")
    void confirmReceipt_replay_ok() throws Exception {
        doNothing().when(applicationService).confirmReceipt(any(ConfirmReceiptCommand.class));
        String body = objectMapper.writeValueAsString(Map.of(
            "orderId", "ORDER-2",
            "idempotencyKey", "ORDER-2#U1"
        ));
        // 首次
        mockMvc.perform(post("/order/user/confirm-receipt")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-Id", "T1")
                .header("X-User-Id", "U1")
                .content(body))
            .andExpect(status().isOk());
        // 重放
        mockMvc.perform(post("/order/user/confirm-receipt")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-Id", "T1")
                .header("X-User-Id", "U1")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    @DisplayName("取消申请：200 且返回包装响应")
    void cancelApply_ok() throws Exception {
        when(applicationService.cancelOrder(any(CancelOrderCommand.class)))
            .thenReturn("待确认");

        String body = objectMapper.writeValueAsString(Map.of(
            "orderId", UUID.randomUUID().toString(),
            "reasonCode", "USER_CHANGED_MIND",
            "clientRequestId", "REQ-1",
            "remark", "no longer needed",
            "idempotencyKey", "CANCEL#1"
        ));

        mockMvc.perform(post("/order/user/cancel/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-Id", "T1")
                .header("X-User-Id", "U1")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("售后申请：200 且返回售后单ID")
    void afterSaleApply_ok() throws Exception {
        when(applicationService.applyAfterSale(any(AfterSaleApplyCommand.class)))
            .thenReturn("AS-1001");

        String orderId = UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(Map.of(
            "orderId", orderId,
            "type", "REFUND",
            "reasonCode", "DAMAGED",
            "idempotencyKey", "AS#" + orderId
        ));

        mockMvc.perform(post("/order/user/after-sale/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-Id", "T1")
                .header("X-User-Id", "U1")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.status").value("success"))
            .andExpect(jsonPath("$.data.afterSaleId").value("AS-1001"));
    }

    @Test
    @DisplayName("售后申请（行级换货）：200 返回售后ID")
    void afterSaleApply_lineLevel_ok() throws Exception {
        when(applicationService.applyAfterSale(any(AfterSaleApplyCommand.class)))
            .thenReturn("AS-LINE-1");
        String orderId = UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(Map.of(
            "orderId", orderId,
            "lineId", "LINE-1",
            "type", "EXCHANGE",
            "reasonCode", "SIZE_NOT_FIT",
            "idempotencyKey", "AS#" + orderId + "#LINE-1"
        ));
        mockMvc.perform(post("/order/user/after-sale/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-Id", "T1")
                .header("X-User-Id", "U1")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.afterSaleId").value("AS-LINE-1"));
    }

    @Test
    @DisplayName("提交订单预览：200 返回预览结构")
    void submitPreview_ok() throws Exception {
        ConfirmOrderResult preview = new ConfirmOrderResult(
            java.util.List.of(),
            new OrderSummary(Money.zero(), Money.zero(), java.util.List.of(), java.util.List.of(), java.util.List.of()),
            System.currentTimeMillis()/1000 + 600
        );
        when(applicationService.orderPreview(any(ConfirmOrderCommand.class))).thenReturn(preview);
        String body = "{" +
            "\"items\":[{\"shopId\":\"M1\",\"products\":[{\"productId\":\"P1\",\"skuId\":\"SKU1\",\"quantity\":1}]}]," +
            "\"addressId\":\"ADDR1\"," +
            "\"deviceId\":\"DEV1\"}";
        mockMvc.perform(post("/order/user/submit/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-Id", "T1")
                .header("X-User-Id", "U1")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.expireAt").exists());
    }

    @Test
    @DisplayName("提交订单：200 返回创建结果占位")
    void submitOrder_ok() throws Exception {
        SubmitOrderResult submitResult = new SubmitOrderResult();
        when(applicationService.submitOrder(any(SubmitOrderCommand.class))).thenReturn(submitResult);
        String body = "{" +
            "\"userId\":\"U1\"," +
            "\"addressId\":\"ADDR1\"," +
            "\"merchantSkuGroups\":[{\"merchantId\":\"M1\",\"merchantAddressId\":\"MADDR1\",\"skuItems\":[{\"skuId\":\"SKU1\",\"quantity\":1}]}]}";
        mockMvc.perform(post("/order/user/submit/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-Id", "T1")
                .header("X-User-Id", "U1")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("提交订单重复调用：仍返回200 (幂等回放占位)")
    void submitOrder_replay_ok() throws Exception {
        SubmitOrderResult submitResult = new SubmitOrderResult();
        when(applicationService.submitOrder(any(SubmitOrderCommand.class))).thenReturn(submitResult);
        String body = "{" +
            "\"userId\":\"U1\"," +
            "\"addressId\":\"ADDR1\"," +
            "\"merchantSkuGroups\":[{\"merchantId\":\"M1\",\"merchantAddressId\":\"MADDR1\",\"skuItems\":[{\"skuId\":\"SKU1\",\"quantity\":1}]}]}";
        mockMvc.perform(post("/order/user/submit/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-Id", "T1")
                .header("X-User-Id", "U1")
                .content(body))
            .andExpect(status().isOk());
        mockMvc.perform(post("/order/user/submit/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-Id", "T1")
                .header("X-User-Id", "U1")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    @DisplayName("取消预览：200 返回预览对象占位")
    void cancelPreview_ok() throws Exception {
        when(applicationService.cancelPreview(any(String.class))).thenReturn(Map.of("canCancel", true));
        String body = objectMapper.writeValueAsString(Map.of(
            "orderId", UUID.randomUUID().toString()
        ));
        mockMvc.perform(post("/order/user/cancel/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-Id", "T1")
                .header("X-User-Id", "U1")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.canCancel").value(true));
    }
}
