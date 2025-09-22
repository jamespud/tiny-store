package com.github.spud.tinystore.order.domain.service;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.spud.tinystore.order.domain.event.OrderStatus;
import com.github.spud.tinystore.order.domain.status.CoreFlowStatus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OrderStatusTranslator 单元测试
 * 测试状态转换器的双向映射正确性和一致性
 * 
 * @author Spud
 * @date 2025/9/22
 */
@DisplayName("订单状态转换器测试")
class OrderStatusTranslatorTest {
    
    private OrderStatusTranslator translator;
    
    @BeforeEach
    void setUp() {
        translator = new OrderStatusTranslator();
    }
    
    @Nested
    @DisplayName("核心状态映射测试")
    class CoreStatusMappingTest {
        
        @Test
        @DisplayName("待支付状态双向映射")
        void shouldCorrectlyMapPendingPaymentBidirectionally() {
            // Given
            CoreFlowStatus coreStatus = CoreFlowStatus.PENDING_PAYMENT;
            OrderStatus legacyStatus = OrderStatus.UNPAID;
            
            // When & Then
            assertEquals(legacyStatus, translator.toLegacy(coreStatus));
            assertEquals(coreStatus, translator.toCore(legacyStatus));
        }
        
        @Test
        @DisplayName("支付确认状态双向映射")
        void shouldCorrectlyMapPaidConfirmedBidirectionally() {
            // Given
            CoreFlowStatus coreStatus = CoreFlowStatus.PAID_CONFIRMED;
            OrderStatus legacyStatus = OrderStatus.PAID;
            
            // When & Then
            assertEquals(legacyStatus, translator.toLegacy(coreStatus));
            assertEquals(coreStatus, translator.toCore(legacyStatus));
        }
        
        @Test
        @DisplayName("待履约状态双向映射")
        void shouldCorrectlyMapAwaitingFulfillmentBidirectionally() {
            // Given
            CoreFlowStatus coreStatus = CoreFlowStatus.AWAITING_FULFILLMENT;
            OrderStatus legacyStatus = OrderStatus.FULFILLMENT_PENDING;
            
            // When & Then
            assertEquals(legacyStatus, translator.toLegacy(coreStatus));
            assertEquals(coreStatus, translator.toCore(legacyStatus));
        }
        
        @Test
        @DisplayName("履约中状态双向映射")
        void shouldCorrectlyMapFulfillingBidirectionally() {
            // Given
            CoreFlowStatus coreStatus = CoreFlowStatus.FULFILLING;
            OrderStatus legacyStatus = OrderStatus.FULFILLING;
            
            // When & Then
            assertEquals(legacyStatus, translator.toLegacy(coreStatus));
            assertEquals(coreStatus, translator.toCore(legacyStatus));
        }
        
        @Test
        @DisplayName("已完成状态双向映射")
        void shouldCorrectlyMapCompletedBidirectionally() {
            // Given
            CoreFlowStatus coreStatus = CoreFlowStatus.COMPLETED;
            OrderStatus legacyStatus = OrderStatus.COMPLETED;
            
            // When & Then
            assertEquals(legacyStatus, translator.toLegacy(coreStatus));
            assertEquals(coreStatus, translator.toCore(legacyStatus));
        }
        
        @Test
        @DisplayName("已取消状态双向映射")
        void shouldCorrectlyMapCancelledBidirectionally() {
            // Given
            CoreFlowStatus coreStatus = CoreFlowStatus.CANCELLED;
            OrderStatus legacyStatus = OrderStatus.CANCELLED;
            
            // When & Then
            assertEquals(legacyStatus, translator.toLegacy(coreStatus));
            assertEquals(coreStatus, translator.toCore(legacyStatus));
        }
    }
    
    @Nested
    @DisplayName("可选映射测试")
    class OptionalMappingTest {
        
        @Test
        @DisplayName("有效核心状态的可选映射")
        void shouldReturnPresentOptionalForValidCoreStatus() {
            // Given
            CoreFlowStatus coreStatus = CoreFlowStatus.PAID_CONFIRMED;
            
            // When
            Optional<OrderStatus> result = translator.toLegacyOptional(coreStatus);
            
            // Then
            assertTrue(result.isPresent());
            assertEquals(OrderStatus.PAID, result.get());
        }
        
        @Test
        @DisplayName("有效遗留状态的可选映射")
        void shouldReturnPresentOptionalForValidLegacyStatus() {
            // Given
            OrderStatus legacyStatus = OrderStatus.FULFILLMENT_PENDING;
            
            // When
            Optional<CoreFlowStatus> result = translator.toCoreOptional(legacyStatus);
            
            // Then
            assertTrue(result.isPresent());
            assertEquals(CoreFlowStatus.AWAITING_FULFILLMENT, result.get());
        }
        
        @Test
        @DisplayName("空值的可选映射")
        void shouldReturnEmptyOptionalForNull() {
            // When & Then
            assertFalse(translator.toLegacyOptional(null).isPresent());
            assertFalse(translator.toCoreOptional(null).isPresent());
        }
    }
    
    @Nested
    @DisplayName("映射一致性测试")
    class MappingConsistencyTest {
        
        @Test
        @DisplayName("所有核心状态都应有对应的遗留状态")
        void shouldHaveLegacyMappingForAllCoreStatuses() {
            for (CoreFlowStatus coreStatus : CoreFlowStatus.values()) {
                assertDoesNotThrow(() -> {
                    OrderStatus legacyStatus = translator.toLegacy(coreStatus);
                    assertNotNull(legacyStatus, 
                        "核心状态 " + coreStatus + " 应该有对应的遗留状态映射");
                }, "核心状态 " + coreStatus + " 的映射不应抛出异常");
            }
        }
        
        @Test
        @DisplayName("映射的对称性验证")
        void shouldMaintainSymmetryInMapping() {
            // 对于每个可以双向映射的状态对，验证对称性
            CoreFlowStatus[] testCoreStatuses = {
                CoreFlowStatus.PENDING_PAYMENT,
                CoreFlowStatus.PAID_CONFIRMED,
                CoreFlowStatus.AWAITING_FULFILLMENT,
                CoreFlowStatus.FULFILLING,
                CoreFlowStatus.COMPLETED,
                CoreFlowStatus.CANCELLED
            };
            
            for (CoreFlowStatus coreStatus : testCoreStatuses) {
                // core -> legacy -> core 应该保持不变
                OrderStatus legacyStatus = translator.toLegacy(coreStatus);
                CoreFlowStatus backToCoreStatus = translator.toCore(legacyStatus);
                
                assertEquals(coreStatus, backToCoreStatus,
                    String.format("状态映射对称性失败: %s -> %s -> %s", 
                        coreStatus, legacyStatus, backToCoreStatus));
            }
        }
        
        @Test
        @DisplayName("终态状态映射验证")
        void shouldCorrectlyMapTerminalStatuses() {
            CoreFlowStatus[] terminalStatuses = {
                CoreFlowStatus.COMPLETED,
                CoreFlowStatus.CANCELLED,
                CoreFlowStatus.CLOSED,
                CoreFlowStatus.REFUNDED
            };
            
            for (CoreFlowStatus terminalStatus : terminalStatuses) {
                assertTrue(terminalStatus.isTerminal(), 
                    "状态 " + terminalStatus + " 应该被标记为终态");
                
                // 终态应该有有效的遗留状态映射
                assertDoesNotThrow(() -> {
                    OrderStatus legacyStatus = translator.toLegacy(terminalStatus);
                    assertNotNull(legacyStatus);
                }, "终态 " + terminalStatus + " 应该有有效的遗留状态映射");
            }
        }
    }
    
    @Nested
    @DisplayName("异常处理测试")
    class ExceptionHandlingTest {
        
        @Test
        @DisplayName("null 输入应该抛出异常")
        void shouldThrowExceptionForNullInput() {
            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                translator.toLegacy(null);
            });
            
            assertThrows(IllegalArgumentException.class, () -> {
                translator.toCore(null);
            });
        }
        
        @Test
        @DisplayName("可选映射方法不应抛出异常")
        void optionalMethodsShouldNotThrowExceptions() {
            // When & Then - 可选方法即使输入 null 也不应抛出异常
            assertDoesNotThrow(() -> {
                Optional<OrderStatus> result1 = translator.toLegacyOptional(null);
                assertFalse(result1.isPresent());
            });
            
            assertDoesNotThrow(() -> {
                Optional<CoreFlowStatus> result2 = translator.toCoreOptional(null);
                assertFalse(result2.isPresent());
            });
        }
    }
    
    @Nested
    @DisplayName("边界情况测试")
    class EdgeCaseTest {
        
        @Test
        @DisplayName("待付尾款状态映射")
        void shouldHandlePendingFinalPaymentMapping() {
            // Given
            CoreFlowStatus coreStatus = CoreFlowStatus.PENDING_FINAL_PAYMENT;
            
            // When
            OrderStatus legacyStatus = translator.toLegacy(coreStatus);
            
            // Then
            // 待付尾款可能映射到特定的遗留状态或者复用已有状态
            assertNotNull(legacyStatus);
            
            // 验证反向映射的一致性（如果可能）
            Optional<CoreFlowStatus> backToCoreOptional = translator.toCoreOptional(legacyStatus);
            if (backToCoreOptional.isPresent()) {
                // 如果有反向映射，应该能映射回来或映射到语义等价的状态
                CoreFlowStatus backToCore = backToCoreOptional.get();
                assertTrue(isSemanticallySimilar(coreStatus, backToCore),
                    String.format("状态 %s 映射到 %s 再映射回 %s 不是语义等价的", 
                        coreStatus, legacyStatus, backToCore));
            }
        }
        
        @Test
        @DisplayName("售后中状态映射")
        void shouldHandleAfterSaleMapping() {
            // Given
            CoreFlowStatus coreStatus = CoreFlowStatus.AFTER_SALE;
            
            // When
            OrderStatus legacyStatus = translator.toLegacy(coreStatus);
            
            // Then
            assertNotNull(legacyStatus);
            
            // 售后状态应该映射到合理的遗留状态（如已发货、已送达等）
            assertTrue(isValidAfterSaleLegacyStatus(legacyStatus),
                "售后状态应该映射到合理的遗留状态");
        }
        
        private boolean isSemanticallySimilar(CoreFlowStatus status1, CoreFlowStatus status2) {
            // 定义语义相似的状态组
            if (status1 == status2) return true;
            
            // 支付相关状态组
            if ((status1 == CoreFlowStatus.PENDING_PAYMENT || status1 == CoreFlowStatus.PENDING_FINAL_PAYMENT) &&
                (status2 == CoreFlowStatus.PENDING_PAYMENT || status2 == CoreFlowStatus.PENDING_FINAL_PAYMENT)) {
                return true;
            }
            
            // 履约相关状态组
            if ((status1 == CoreFlowStatus.FULFILLING || status1 == CoreFlowStatus.AFTER_SALE) &&
                (status2 == CoreFlowStatus.FULFILLING || status2 == CoreFlowStatus.AFTER_SALE)) {
                return true;
            }
            
            return false;
        }
        
        private boolean isValidAfterSaleLegacyStatus(OrderStatus status) {
            // 售后状态应该映射到表示已发货或已送达的状态
            return status == OrderStatus.SHIPPED || 
                   status == OrderStatus.DELIVERED || 
                   status == OrderStatus.COMPLETED ||
                   status.name().contains("DELIVERED") ||
                   status.name().contains("SHIPPED");
        }
    }
}