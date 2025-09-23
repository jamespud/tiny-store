package com.github.spud.tinystore.order.domain.status;

import java.util.Arrays;
import java.util.List;

/**
 * Status Priority Matrix for deriving single status from multiple status dimensions
 * Implements priority-based rules to resolve conflicts between different status domains
 * 
 * @author Spud
 * @date 2025/9/6
 */
public class StatusPriorityMatrix {

    /**
     * Derive the final status view based on multiple status dimensions and priority rules
     */
    public static DerivedStatusView derive(DerivedStatusView.DeriveContext context) {
        // Priority order (higher priority first):
        // 1. System exceptions override everything
        // 2. Terminal states (completed/cancelled/refunded/closed)  
        // 3. Active processes (cancelling/after-sale)
        // 4. Payment states
        // 5. Core flow states
        
        // Rule 1: System exceptions take highest priority
        if (context.hasException()) {
            return DerivedStatusView.SYSTEM_ERROR;
        }
        
        // Rule 2: Terminal states (check core flow first)
        if (TerminalStateChecker.isTerminal(context.getCoreFlowStatus())) {
            return switch (context.getCoreFlowStatus()) {
                case COMPLETED -> DerivedStatusView.COMPLETED;
                case CANCELLED -> DerivedStatusView.CANCELLED;
                case REFUNDED -> DerivedStatusView.REFUNDED;
                case CLOSED -> DerivedStatusView.CLOSED;
                default -> deriveFromCoreFlow(context);
            };
        }
        
        // Rule 3: Active cancellation process (overrides most other states)
        if (context.getCancellationStatus() == CancellationStatus.REQUESTED ||
            context.getCoreFlowStatus() == CoreFlowStatus.CANCELLING) {
            return switch (context.getViewType()) {
                case MERCHANT_VIEW, CS_VIEW -> DerivedStatusView.REVIEW_PENDING;
                default -> DerivedStatusView.CANCELLING;
            };
        }
        
        // Rule 4: Active after-sale process
        if (context.getCoreFlowStatus() == CoreFlowStatus.AFTER_SALE ||
            context.getAfterSaleStatus() != AfterSaleStatus.NONE) {
            return switch (context.getAfterSaleStatus()) {
                case REVIEWING -> DerivedStatusView.REVIEW_PENDING;
                case PROCESSING -> DerivedStatusView.AFTER_SALE;
                case NEGOTIATION_PENDING -> DerivedStatusView.REVIEW_PENDING;
                default -> DerivedStatusView.AFTER_SALE;
            };
        }
        
        // Rule 5: Payment-related states (high priority for incomplete payments)
        DerivedStatusView paymentDerived = deriveFromPaymentStatus(context);
        if (paymentDerived != null) {
            return paymentDerived;
        }
        
        // Rule 6: Core flow states (default derivation)
        return deriveFromCoreFlow(context);
    }
    
    /**
     * Derive status primarily from payment status
     */
    private static DerivedStatusView deriveFromPaymentStatus(DerivedStatusView.DeriveContext context) {
        return switch (context.getPaymentStatus()) {
            case PAYMENT_PENDING -> DerivedStatusView.WAITING_FOR_PAYMENT;
            case PAYMENT_PROCESSING -> DerivedStatusView.PAYMENT_PROCESSING;
            case DEPOSIT_PAID -> DerivedStatusView.WAITING_FOR_DEPOSIT;
            case FINAL_PAYMENT_PENDING -> DerivedStatusView.WAITING_FOR_FINAL_PAYMENT;
            case PAYMENT_FAILED -> DerivedStatusView.SYSTEM_ERROR;
            default -> null; // No specific payment state, check core flow
        };
    }
    
    /**
     * Derive status from core flow status with view-specific adjustments
     */
    private static DerivedStatusView deriveFromCoreFlow(DerivedStatusView.DeriveContext context) {
        CoreFlowStatus coreStatus = context.getCoreFlowStatus();
        DerivedStatusView.ViewType viewType = context.getViewType();
        
        return switch (coreStatus) {
            case CREATED -> DerivedStatusView.WAITING_FOR_PAYMENT;
            case PENDING_PAYMENT -> DerivedStatusView.WAITING_FOR_PAYMENT;
            case PENDING_FINAL_PAYMENT -> DerivedStatusView.WAITING_FOR_FINAL_PAYMENT;
            case PAID_CONFIRMED -> switch (viewType) {
                case MERCHANT_VIEW -> DerivedStatusView.PENDING_MERCHANT_ACCEPT;
                default -> DerivedStatusView.WAITING_FOR_SHIPMENT;
            };
            case AWAITING_FULFILLMENT -> switch (viewType) {
                case MERCHANT_VIEW -> DerivedStatusView.MERCHANT_PROCESSING;
                default -> DerivedStatusView.WAITING_FOR_SHIPMENT;
            };
            case FULFILLING -> DerivedStatusView.IN_TRANSIT;
            case COMPLETED -> DerivedStatusView.COMPLETED;
            case CANCELLED -> DerivedStatusView.CANCELLED;
            case CLOSED -> DerivedStatusView.CLOSED;
            case REFUNDED -> DerivedStatusView.REFUNDED;
            case CANCELLING -> DerivedStatusView.CANCELLING;
            case AFTER_SALE -> DerivedStatusView.AFTER_SALE;
        };
    }
    
    /**
     * Get all possible status transitions for a given current status and view
     */
    public static List<DerivedStatusView> getPossibleTransitions(DerivedStatusView current, 
                                                               DerivedStatusView.ViewType viewType) {
        return switch (current) {
            case WAITING_FOR_PAYMENT -> Arrays.asList(
                DerivedStatusView.PAYMENT_PROCESSING,
                DerivedStatusView.CANCELLED,
                DerivedStatusView.SYSTEM_ERROR
            );
            case WAITING_FOR_DEPOSIT -> Arrays.asList(
                DerivedStatusView.WAITING_FOR_FINAL_PAYMENT,
                DerivedStatusView.CANCELLED
            );
            case WAITING_FOR_FINAL_PAYMENT -> Arrays.asList(
                DerivedStatusView.WAITING_FOR_SHIPMENT,
                DerivedStatusView.CANCELLED
            );
            case WAITING_FOR_SHIPMENT -> Arrays.asList(
                DerivedStatusView.IN_TRANSIT,
                DerivedStatusView.CANCELLING,
                DerivedStatusView.CANCELLED
            );
            case IN_TRANSIT -> Arrays.asList(
                DerivedStatusView.DELIVERED,
                DerivedStatusView.COMPLETED,
                DerivedStatusView.AFTER_SALE
            );
            case DELIVERED, COMPLETED -> Arrays.asList(
                DerivedStatusView.AFTER_SALE,
                DerivedStatusView.REFUNDED
            );
            case CANCELLING -> Arrays.asList(
                DerivedStatusView.CANCELLED,
                DerivedStatusView.WAITING_FOR_SHIPMENT, // If rejection
                DerivedStatusView.IN_TRANSIT // If rejection
            );
            case AFTER_SALE -> Arrays.asList(
                DerivedStatusView.REFUNDED,
                DerivedStatusView.COMPLETED,
                DerivedStatusView.CLOSED
            );
            default -> Arrays.asList(); // Terminal states have no transitions
        };
    }
    
    /**
     * Check if a status transition is valid
     */
    public static boolean isValidTransition(DerivedStatusView from, DerivedStatusView to, 
                                          DerivedStatusView.ViewType viewType) {
        List<DerivedStatusView> validTransitions = getPossibleTransitions(from, viewType);
        return validTransitions.contains(to);
    }
    
    /**
     * Get status description with context
     */
    public static String getStatusDescription(DerivedStatusView status, 
                                            DerivedStatusView.ViewType viewType) {
        return switch (viewType) {
            case USER_VIEW -> getUserFriendlyDescription(status);
            case MERCHANT_VIEW -> getMerchantDescription(status);
            case CS_VIEW -> getCSDescription(status);
            case ADMIN_VIEW -> getAdminDescription(status);
        };
    }
    
    private static String getUserFriendlyDescription(DerivedStatusView status) {
        return switch (status) {
            case WAITING_FOR_PAYMENT -> "请尽快完成支付";
            case WAITING_FOR_SHIPMENT -> "商家正在准备发货";
            case IN_TRANSIT -> "包裹正在配送中";
            case DELIVERED -> "包裹已送达，请确认收货";
            case COMPLETED -> "交易完成";
            case CANCELLING -> "取消申请审核中";
            case CANCELLED -> "订单已取消";
            case AFTER_SALE -> "售后处理中";
            case REFUNDED -> "已退款";
            default -> status.getDisplayName();
        };
    }
    
    private static String getMerchantDescription(DerivedStatusView status) {
        return switch (status) {
            case PENDING_MERCHANT_ACCEPT -> "等待您接单处理";
            case MERCHANT_PROCESSING -> "请尽快安排发货";
            case REVIEW_PENDING -> "需要您审核处理";
            case CANCELLING -> "买家申请取消，请及时处理";
            default -> status.getDisplayName();
        };
    }
    
    private static String getCSDescription(DerivedStatusView status) {
        return switch (status) {
            case REVIEW_PENDING -> "待客服审核处理";
            case EXCEPTION_HANDLING -> "异常情况需要介入";
            case SYSTEM_ERROR -> "系统异常，需要技术处理";
            default -> status.getDisplayName();
        };
    }
    
    private static String getAdminDescription(DerivedStatusView status) {
        return switch (status) {
            case SYSTEM_ERROR -> "系统异常：需要检查日志和数据一致性";
            case EXCEPTION_HANDLING -> "业务异常：需要分析根因和处理方案";
            default -> status.getDisplayName() + " (技术视图)";
        };
    }
}
