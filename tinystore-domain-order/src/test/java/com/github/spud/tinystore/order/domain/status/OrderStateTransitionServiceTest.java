package com.github.spud.tinystore.order.domain.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OrderStateTransitionService 单元测试
 * 测试状态机不变性、非法跳转阻止、幂等性重放等核心行为
 * 
 * @author Spud
 * @date 2025/9/22
 */
@DisplayName("订单状态机转换服务测试")
class OrderStateTransitionServiceTest {
    
    private OrderStateTransitionService transitionService;
    
    @BeforeEach
    void setUp() {
        transitionService = new OrderStateTransitionService();
    }
    
    @Nested
    @DisplayName("支付成功转换测试")
    class PaymentSuccessTransitionTest {
        
        @Test
        @DisplayName("从待支付到支付确认 - 全款支付")
        void shouldTransitionFromPendingPaymentToPaidConfirmed() {
            // Given
            CoreFlowStatus currentStatus = CoreFlowStatus.PENDING_PAYMENT;
            boolean isDeposit = false;
            boolean isFinalPayment = true;
            
            // When
            CoreFlowStatus newStatus = transitionService.paymentSuccess(currentStatus, isDeposit, isFinalPayment);
            
            // Then
            assertEquals(CoreFlowStatus.PAID_CONFIRMED, newStatus);
        }
        
        @Test
        @DisplayName("从待支付到待付尾款 - 定金支付")
        void shouldTransitionFromPendingPaymentToPendingFinalPayment() {
            // Given
            CoreFlowStatus currentStatus = CoreFlowStatus.PENDING_PAYMENT;
            boolean isDeposit = true;
            boolean isFinalPayment = false;
            
            // When
            CoreFlowStatus newStatus = transitionService.paymentSuccess(currentStatus, isDeposit, isFinalPayment);
            
            // Then
            assertEquals(CoreFlowStatus.PENDING_FINAL_PAYMENT, newStatus);
        }
        
        @Test
        @DisplayName("从待付尾款到支付确认 - 尾款支付")
        void shouldTransitionFromPendingFinalPaymentToPaidConfirmed() {
            // Given
            CoreFlowStatus currentStatus = CoreFlowStatus.PENDING_FINAL_PAYMENT;
            boolean isDeposit = false;
            boolean isFinalPayment = true;
            
            // When
            CoreFlowStatus newStatus = transitionService.paymentSuccess(currentStatus, isDeposit, isFinalPayment);
            
            // Then
            assertEquals(CoreFlowStatus.PAID_CONFIRMED, newStatus);
        }
        
        @Test
        @DisplayName("幂等性测试 - 重复支付成功不改变状态")
        void shouldBeIdempotentForRepeatPaymentSuccess() {
            // Given
            CoreFlowStatus currentStatus = CoreFlowStatus.PAID_CONFIRMED;
            boolean isDeposit = false;
            boolean isFinalPayment = true;
            
            // When
            CoreFlowStatus newStatus = transitionService.paymentSuccess(currentStatus, isDeposit, isFinalPayment);
            
            // Then - 状态不变
            assertEquals(CoreFlowStatus.PAID_CONFIRMED, newStatus);
        }
        
        @Test
        @DisplayName("非法转换 - 从已完成状态支付应该抛出异常")
        void shouldThrowExceptionForIllegalTransitionFromCompleted() {
            // Given
            CoreFlowStatus currentStatus = CoreFlowStatus.COMPLETED;
            boolean isDeposit = false;
            boolean isFinalPayment = true;
            
            // When & Then
            assertThrows(IllegalStateException.class, () -> {
                transitionService.paymentSuccess(currentStatus, isDeposit, isFinalPayment);
            });
        }
        
        @Test
        @DisplayName("非法转换 - 从已取消状态支付应该抛出异常")
        void shouldThrowExceptionForIllegalTransitionFromCancelled() {
            // Given
            CoreFlowStatus currentStatus = CoreFlowStatus.CANCELLED;
            
            // When & Then
            assertThrows(IllegalStateException.class, () -> {
                transitionService.paymentSuccess(currentStatus, false, true);
            });
        }
    }
    
    @Nested
    @DisplayName("商家接单转换测试")
    class MerchantAcceptTransitionTest {
        
        @Test
        @DisplayName("从支付确认到待履约")
        void shouldTransitionFromPaidConfirmedToAwaitingFulfillment() {
            // Given
            CoreFlowStatus currentStatus = CoreFlowStatus.PAID_CONFIRMED;
            
            // When
            CoreFlowStatus newStatus = transitionService.moveToAwaitingFulfillment(currentStatus);
            
            // Then
            assertEquals(CoreFlowStatus.AWAITING_FULFILLMENT, newStatus);
        }
        
        @Test
        @DisplayName("幂等性测试 - 重复接单不改变状态")
        void shouldBeIdempotentForRepeatAccept() {
            // Given
            CoreFlowStatus currentStatus = CoreFlowStatus.AWAITING_FULFILLMENT;
            
            // When
            CoreFlowStatus newStatus = transitionService.moveToAwaitingFulfillment(currentStatus);
            
            // Then - 状态不变
            assertEquals(CoreFlowStatus.AWAITING_FULFILLMENT, newStatus);
        }
        
        @Test
        @DisplayName("非法转换 - 从待支付接单应该抛出异常")
        void shouldThrowExceptionForAcceptFromPendingPayment() {
            // Given
            CoreFlowStatus currentStatus = CoreFlowStatus.PENDING_PAYMENT;
            
            // When & Then
            assertThrows(IllegalStateException.class, () -> {
                transitionService.moveToAwaitingFulfillment(currentStatus);
            });
        }
    }
    
    @Nested
    @DisplayName("发货转换测试")
    class ShipmentTransitionTest {
        
        @Test
        @DisplayName("从待履约到履约中")
        void shouldTransitionFromAwaitingFulfillmentToFulfilling() {
            // Given
            CoreFlowStatus currentStatus = CoreFlowStatus.AWAITING_FULFILLMENT;
            
            // When
            CoreFlowStatus newStatus = transitionService.startFulfillment(currentStatus);
            
            // Then
            assertEquals(CoreFlowStatus.FULFILLING, newStatus);
        }
        
        @Test
        @DisplayName("幂等性测试 - 重复发货不改变状态")
        void shouldBeIdempotentForRepeatShipment() {
            // Given
            CoreFlowStatus currentStatus = CoreFlowStatus.FULFILLING;
            
            // When
            CoreFlowStatus newStatus = transitionService.startFulfillment(currentStatus);
            
            // Then - 状态不变
            assertEquals(CoreFlowStatus.FULFILLING, newStatus);
        }
        
        @Test
        @DisplayName("非法转换 - 从待支付发货应该抛出异常")
        void shouldThrowExceptionForShipFromPendingPayment() {
            // Given
            CoreFlowStatus currentStatus = CoreFlowStatus.PENDING_PAYMENT;
            
            // When & Then
            assertThrows(IllegalStateException.class, () -> {
                transitionService.startFulfillment(currentStatus);
            });
        }
    }
    
    @Nested
    @DisplayName("妥投转换测试")
    class DeliveredTransitionTest {
        
        @Test
        @DisplayName("从履约中到售后中 - 开启售后观察期")
        void shouldTransitionFromFulfillingToAfterSaleWithWindow() {
            // Given
            CoreFlowStatus currentStatus = CoreFlowStatus.FULFILLING;
            boolean afterSaleWindowOpen = true;
            
            // When
            CoreFlowStatus newStatus = transitionService.delivered(currentStatus, afterSaleWindowOpen);
            
            // Then
            assertEquals(CoreFlowStatus.AFTER_SALE, newStatus);
        }
        
        @Test
        @DisplayName("从履约中到已完成 - 不开启售后观察期")
        void shouldTransitionFromFulfillingToCompletedWithoutWindow() {
            // Given
            CoreFlowStatus currentStatus = CoreFlowStatus.FULFILLING;
            boolean afterSaleWindowOpen = false;
            
            // When
            CoreFlowStatus newStatus = transitionService.delivered(currentStatus, afterSaleWindowOpen);
            
            // Then
            assertEquals(CoreFlowStatus.COMPLETED, newStatus);
        }
        
        @Test
        @DisplayName("幂等性测试 - 重复妥投到售后状态")
        void shouldBeIdempotentForRepeatDeliveredToAfterSale() {
            // Given
            CoreFlowStatus currentStatus = CoreFlowStatus.AFTER_SALE;
            boolean afterSaleWindowOpen = true;
            
            // When
            CoreFlowStatus newStatus = transitionService.delivered(currentStatus, afterSaleWindowOpen);
            
            // Then - 状态不变
            assertEquals(CoreFlowStatus.AFTER_SALE, newStatus);
        }
        
        @Test
        @DisplayName("非法转换 - 从待支付妥投应该抛出异常")
        void shouldThrowExceptionForDeliveredFromPendingPayment() {
            // Given
            CoreFlowStatus currentStatus = CoreFlowStatus.PENDING_PAYMENT;
            
            // When & Then
            assertThrows(IllegalStateException.class, () -> {
                transitionService.delivered(currentStatus, true);
            });
        }
    }
    
    @Nested
    @DisplayName("完成转换测试")
    class CompletionTransitionTest {
        
        @Test
        @DisplayName("从售后中到已完成 - 无售后问题")
        void shouldTransitionFromAfterSaleToCompleted() {
            // Given
            CoreFlowStatus currentStatus = CoreFlowStatus.AFTER_SALE;
            
            // When
            CoreFlowStatus newStatus = transitionService.completeIfNoAfterSale(currentStatus);
            
            // Then
            assertEquals(CoreFlowStatus.COMPLETED, newStatus);
        }
        
        @Test
        @DisplayName("幂等性测试 - 重复完成不改变状态")
        void shouldBeIdempotentForRepeatCompletion() {
            // Given
            CoreFlowStatus currentStatus = CoreFlowStatus.COMPLETED;
            
            // When
            CoreFlowStatus newStatus = transitionService.completeIfNoAfterSale(currentStatus);
            
            // Then - 状态不变
            assertEquals(CoreFlowStatus.COMPLETED, newStatus);
        }
        
        @Test
        @DisplayName("非法转换 - 从待支付完成应该抛出异常")
        void shouldThrowExceptionForCompletionFromPendingPayment() {
            // Given
            CoreFlowStatus currentStatus = CoreFlowStatus.PENDING_PAYMENT;
            
            // When & Then
            assertThrows(IllegalStateException.class, () -> {
                transitionService.completeIfNoAfterSale(currentStatus);
            });
        }
    }
    
    @Nested
    @DisplayName("状态机不变性测试")
    class StateMachineInvariantTest {
        
        @Test
        @DisplayName("终态不可变性 - 已完成状态不能转换")
        void shouldNotAllowTransitionsFromTerminalStates() {
            CoreFlowStatus[] terminalStates = {
                CoreFlowStatus.COMPLETED,
                CoreFlowStatus.CANCELLED,
                CoreFlowStatus.CLOSED,
                CoreFlowStatus.REFUNDED
            };
            
            for (CoreFlowStatus terminalState : terminalStates) {
                // 验证终态标记
                assertTrue(terminalState.isTerminal(), 
                    "状态 " + terminalState + " 应该被标记为终态");
                
                // 验证不能从终态进行非幂等转换
                if (terminalState != CoreFlowStatus.COMPLETED) {
                    assertThrows(IllegalStateException.class, () -> {
                        transitionService.completeIfNoAfterSale(terminalState);
                    }, "不应该允许从终态 " + terminalState + " 转换到完成");
                }
            }
        }
        
        @Test
        @DisplayName("状态转换路径完整性")
        void shouldCoverAllValidTransitionPaths() {
            // 主链路径：待支付 -> 支付确认 -> 待履约 -> 履约中 -> 售后中 -> 已完成
            CoreFlowStatus status = CoreFlowStatus.PENDING_PAYMENT;
            
            // 支付成功
            status = transitionService.paymentSuccess(status, false, true);
            assertEquals(CoreFlowStatus.PAID_CONFIRMED, status);
            
            // 商家接单
            status = transitionService.moveToAwaitingFulfillment(status);
            assertEquals(CoreFlowStatus.AWAITING_FULFILLMENT, status);
            
            // 商家发货
            status = transitionService.startFulfillment(status);
            assertEquals(CoreFlowStatus.FULFILLING, status);
            
            // 物流妥投（开启售后观察期）
            status = transitionService.delivered(status, true);
            assertEquals(CoreFlowStatus.AFTER_SALE, status);
            
            // 无售后问题，自动完成
            status = transitionService.completeIfNoAfterSale(status);
            assertEquals(CoreFlowStatus.COMPLETED, status);
            
            // 验证已达到终态
            assertTrue(status.isTerminal());
        }
        
        @Test
        @DisplayName("定金支付路径测试")
        void shouldHandleDepositPaymentPath() {
            // 定金支付路径：待支付 -> 待付尾款 -> 支付确认
            CoreFlowStatus status = CoreFlowStatus.PENDING_PAYMENT;
            
            // 定金支付
            status = transitionService.paymentSuccess(status, true, false);
            assertEquals(CoreFlowStatus.PENDING_FINAL_PAYMENT, status);
            
            // 尾款支付
            status = transitionService.paymentSuccess(status, false, true);
            assertEquals(CoreFlowStatus.PAID_CONFIRMED, status);
        }
        
        @Test
        @DisplayName("用户主动确认收货路径测试")
        void shouldHandleUserConfirmReceiptPath() {
            // 用户主动确认收货：履约中 -> 已完成（跳过售后观察期）
            CoreFlowStatus status = CoreFlowStatus.FULFILLING;
            
            // 用户确认收货（不开启售后观察期）
            status = transitionService.delivered(status, false);
            assertEquals(CoreFlowStatus.COMPLETED, status);
            
            // 验证已达到终态
            assertTrue(status.isTerminal());
        }
    }
}