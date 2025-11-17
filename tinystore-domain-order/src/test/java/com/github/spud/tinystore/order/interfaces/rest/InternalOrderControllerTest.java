package com.github.spud.tinystore.order.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.application.command.AutoCompleteCommand;
import com.github.spud.tinystore.order.application.command.MoveToAwaitFulfillmentCommand;
import com.github.spud.tinystore.order.application.command.PaymentSucceededCommand;
import com.github.spud.tinystore.order.application.command.RefundSucceededCommand;
import com.github.spud.tinystore.order.application.command.merchant.DeliveredCommand;
import com.github.spud.tinystore.order.application.command.UnpaidTimeoutCancelCommand;
import com.github.spud.tinystore.order.application.service.OrderApplicationService;
import java.math.BigDecimal;
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
@WebMvcTest(InternalOrderController.class)
@ContextConfiguration(classes = {InternalOrderController.class, InternalOrderControllerTest.Config.class})
@AutoConfigureMockMvc(addFilters = false)
class InternalOrderControllerTest {

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
    @DisplayName("支付成功回调：200 返回成功ACK")
    void paymentSuccess_ok() throws Exception {
        doNothing().when(applicationService).onPaymentSuccess(any(PaymentSucceededCommand.class));

        String body = objectMapper.writeValueAsString(Map.of(
            "orderId", "ORDER-I-1",
            "payType", "FULL",
            "payAmount", new BigDecimal("99.90"),
            "paidAt", System.currentTimeMillis(),
            "paymentId", "PAY123",
            "eventId", UUID.randomUUID().toString()
        ));

        mockMvc.perform(post("/order/internal/payment/success")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.status").value("success"));
    }

    @Test
    @DisplayName("支付成功回调：重复提交两次均返回200（幂等回放）")
    void paymentSuccess_replay_ok_twice() throws Exception {
        doNothing().when(applicationService).onPaymentSuccess(any(PaymentSucceededCommand.class));

        String idempotentKey = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(Map.of(
            "orderId", "ORDER-I-REPLAY-1",
            "payType", "FULL",
            "payAmount", new java.math.BigDecimal("88.00"),
            "paidAt", System.currentTimeMillis(),
            "paymentId", "PAY-RE-1",
            "eventId", eventId
        ));

        mockMvc.perform(post("/order/internal/payment/success")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", idempotentKey)
                .content(body))
            .andExpect(status().isOk());

        // 重复提交相同eventId & 幂等键
        mockMvc.perform(post("/order/internal/payment/success")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", idempotentKey)
                .content(body))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("物流妥投回调：200 返回成功ACK")
    void logisticsDelivered_ok() throws Exception {
        doNothing().when(applicationService).onLogisticsDelivered(any(DeliveredCommand.class));

        String body = objectMapper.writeValueAsString(Map.of(
            "orderId", "ORDER-I-2",
            "trackingNo", "SF123",
            "deliveredAt", System.currentTimeMillis(),
            "eventId", UUID.randomUUID().toString(),
            "source", "LOGISTICS"
        ));

        mockMvc.perform(post("/order/internal/logistics/delivered")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.status").value("success"));
    }

    @Test
    @DisplayName("物流妥投回调：重复提交两次均返回200（幂等回放）")
    void logisticsDelivered_replay_ok_twice() throws Exception {
        doNothing().when(applicationService).onLogisticsDelivered(any(DeliveredCommand.class));

        String idempotentKey = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(Map.of(
            "orderId", "ORDER-I-REPLAY-2",
            "trackingNo", "SF-RE-2",
            "deliveredAt", System.currentTimeMillis(),
            "eventId", eventId,
            "source", "LOGISTICS"
        ));

        mockMvc.perform(post("/order/internal/logistics/delivered")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", idempotentKey)
                .content(body))
            .andExpect(status().isOk());
        mockMvc.perform(post("/order/internal/logistics/delivered")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", idempotentKey)
                .content(body))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("退款成功回调：200 返回成功ACK")
    void refundSuccess_ok() throws Exception {
        doNothing().when(applicationService).onRefundSuccess(any(RefundSucceededCommand.class));

        String body = objectMapper.writeValueAsString(Map.of(
            "orderId", UUID.randomUUID().toString(),
            "refundId", "R-1",
            "amount", new BigDecimal("10.00"),
            "time", System.currentTimeMillis(),
            "eventId", UUID.randomUUID().toString()
        ));

        mockMvc.perform(post("/order/internal/refund/success")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.status").value("success"));
    }

    @Test
    @DisplayName("退款成功回调：重复提交两次均返回200（幂等回放）")
    void refundSuccess_replay_ok_twice() throws Exception {
        doNothing().when(applicationService).onRefundSuccess(any(RefundSucceededCommand.class));

        String idempotentKey = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        String orderUuid = UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(Map.of(
            "orderId", orderUuid,
            "refundId", "R-RE-1",
            "amount", new java.math.BigDecimal("5.00"),
            "time", System.currentTimeMillis(),
            "eventId", eventId
        ));

        mockMvc.perform(post("/order/internal/refund/success")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", idempotentKey)
                .content(body))
            .andExpect(status().isOk());
        mockMvc.perform(post("/order/internal/refund/success")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", idempotentKey)
                .content(body))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("未支付超时取消：200 返回成功ACK")
    void unpaidTimeout_ok() throws Exception {
        doNothing().when(applicationService).timeoutCancel(any(UnpaidTimeoutCancelCommand.class));

        String body = objectMapper.writeValueAsString(Map.of(
            "orderId", UUID.randomUUID().toString(),
            "scheduleId", "JOB-1",
            "eventId", UUID.randomUUID().toString()
        ));

        mockMvc.perform(post("/order/internal/timeout/unpaid-cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.status").value("success"));
    }

    @Test
    @DisplayName("未支付超时取消：重复提交两次均返回200（幂等回放）")
    void unpaidTimeout_replay_ok_twice() throws Exception {
        doNothing().when(applicationService).timeoutCancel(any(UnpaidTimeoutCancelCommand.class));

        String eventId = UUID.randomUUID().toString();
        String orderUuid = UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(Map.of(
            "orderId", orderUuid,
            "scheduleId", "JOB-RE-1",
            "eventId", eventId
        ));

        mockMvc.perform(post("/order/internal/timeout/unpaid-cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .content(body))
            .andExpect(status().isOk());
        mockMvc.perform(post("/order/internal/timeout/unpaid-cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .content(body))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("自动完成：200 返回成功ACK")
    void autoComplete_ok() throws Exception {
        doNothing().when(applicationService).autoComplete(any(AutoCompleteCommand.class));

        String body = objectMapper.writeValueAsString(Map.of(
            "orderId", "ORDER-I-3",
            "graceDays", 7,
            "scheduledAt", System.currentTimeMillis(),
            "eventId", UUID.randomUUID().toString()
        ));

        mockMvc.perform(post("/order/internal/auto/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.status").value("success"));
    }

    @Test
    @DisplayName("自动完成：重复提交两次均返回200（幂等回放）")
    void autoComplete_replay_ok_twice() throws Exception {
        doNothing().when(applicationService).autoComplete(any(AutoCompleteCommand.class));

        String idempotentKey = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(Map.of(
            "orderId", "ORDER-I-REPLAY-5",
            "graceDays", 5,
            "scheduledAt", System.currentTimeMillis(),
            "eventId", eventId
        ));

        mockMvc.perform(post("/order/internal/auto/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", idempotentKey)
                .content(body))
            .andExpect(status().isOk());
        mockMvc.perform(post("/order/internal/auto/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", idempotentKey)
                .content(body))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("支付后自动转待履约：200 返回成功ACK")
    void awaitFulfillment_ok() throws Exception {
        doNothing().when(applicationService).moveToAwaitFulfillment(any(MoveToAwaitFulfillmentCommand.class));

        String body = objectMapper.writeValueAsString(Map.of(
            "orderId", UUID.randomUUID().toString(),
            "eventId", UUID.randomUUID().toString()
        ));

        mockMvc.perform(post("/order/internal/auto/await-fulfillment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.status").value("success"));
    }
}
