package com.github.spud.tinystore.payment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.infrastructure.rpc.order.OrderClient;
import com.github.spud.tinystore.payment.infrastructure.persistence.jpa.entity.PaymentOrderEntity;
import com.github.spud.tinystore.payment.infrastructure.persistence.jpa.repository.PaymentOrderJpaRepository;
import com.github.spud.tinystore.payment.test.it.AbstractPaymentIT;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PaymentApplicationService 集成测试
 * 验证核心支付单创建逻辑与幂等性
 * 
 * @author Spud
 * @date 2026/01/31
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentApplicationServiceIT extends AbstractPaymentIT {

    @Autowired
    private PaymentApplicationService paymentApplicationService;

    @Autowired
    private PaymentOrderJpaRepository paymentOrderRepository;
    
    // Mock OrderClient 避免实际网络调用
    @MockitoBean
    private OrderClient orderClient;

    /**
     * 测试：从 PAYMENT_INTENT_CREATED 事件创建支付单
     */
    @Test
    @Transactional
    void testCreatePaymentOrderFromIntent_shouldCreatePaymentOrder() {
        // Given
        String paymentIntentId = UUID.randomUUID().toString();
        String tradeId = "trade-test-001";
        String buyerId = "buyer-001";
        Long amountCents = 10000L;
        String payChannel = "WECHAT";
        String expireAt = "2026-01-31T18:00:00";

        // When
        paymentApplicationService.createPaymentOrderFromIntent(
            paymentIntentId, tradeId, buyerId, amountCents, payChannel, expireAt
        );

        // Then
        PaymentOrderEntity paymentOrder = paymentOrderRepository.findByPaymentIntentId(paymentIntentId)
            .orElse(null);
        
        assertNotNull(paymentOrder, "PaymentOrder should be created");
        assertEquals(paymentIntentId, paymentOrder.getPaymentIntentId());
        assertEquals(tradeId, paymentOrder.getTradeId());
        assertEquals(buyerId, paymentOrder.getBuyerId());
        assertEquals(amountCents, paymentOrder.getAmountCents());
        assertEquals(payChannel, paymentOrder.getPayChannel());
        assertEquals("UNPAID", paymentOrder.getStatus());
    }

    /**
     * 测试：重复事件幂等性（同一 paymentIntentId 重复投递不应重复创建）
     */
    @Test
    @Transactional
    void testCreatePaymentOrderFromIntent_shouldBeIdempotent() {
        // Given
        String paymentIntentId = UUID.randomUUID().toString();
        String tradeId = "trade-test-002";
        String buyerId = "buyer-002";
        Long amountCents = 20000L;

        // When - 第一次创建
        paymentApplicationService.createPaymentOrderFromIntent(
            paymentIntentId, tradeId, buyerId, amountCents, "ALIPAY", null
        );

        // When - 第二次创建（模拟重复事件）
        paymentApplicationService.createPaymentOrderFromIntent(
            paymentIntentId, tradeId, buyerId, amountCents, "ALIPAY", null
        );

        // Then - 仅创建一条记录
        long count = paymentOrderRepository.findAll().stream()
            .filter(po -> po.getPaymentIntentId().equals(paymentIntentId))
            .count();
        
        assertEquals(1, count, "Should create only one PaymentOrder even with duplicate events");
    }

    /**
     * 测试：查询支付状态
     */
    @Test
    @Transactional
    void testQueryPayState_shouldReturnCorrectStatus() {
        // Given - 创建支付单
        String paymentIntentId = UUID.randomUUID().toString();
        paymentApplicationService.createPaymentOrderFromIntent(
            paymentIntentId, "trade-003", "buyer-003", 5000L, "DEFAULT", null
        );

        // When
        var result = paymentApplicationService.queryPayState(paymentIntentId);

        // Then
        assertNotNull(result);
        assertEquals("UNPAID", result.get("status"));
        assertNotNull(result.get("paymentOrderId"));
        assertEquals(5000L, result.get("amountCents"));
    }

    /**
     * 测试：关闭支付单
     */
    @Test
    @Transactional
    void testClosePayOrder_shouldUpdateStatus() {
        // Given - 创建支付单
        String paymentIntentId = UUID.randomUUID().toString();
        paymentApplicationService.createPaymentOrderFromIntent(
            paymentIntentId, "trade-004", "buyer-004", 3000L, "DEFAULT", null
        );

        // When
        var result = paymentApplicationService.closePayOrder(paymentIntentId);

        // Then
        assertTrue((Boolean) result.get("success"));
        
        PaymentOrderEntity closed = paymentOrderRepository.findByPaymentIntentId(paymentIntentId)
            .orElse(null);
        assertNotNull(closed);
        assertEquals("CLOSED", closed.getStatus());
        assertNotNull(closed.getClosedAt());
    }
}
