package com.github.spud.tinystore.order.domain.status;

/**
 * Derived status views for different user interfaces
 * Provides simplified, user-friendly status representation
 * 
 * @author Spud
 * @date 2025/9/6
 */
public enum DerivedStatusView {
    
    // Common statuses for user view
    WAITING_FOR_PAYMENT("待支付", "WAITING_FOR_PAYMENT"),
    PAYMENT_PROCESSING("支付处理中", "PAYMENT_PROCESSING"),
    WAITING_FOR_DEPOSIT("待付定金", "WAITING_FOR_DEPOSIT"),
    WAITING_FOR_FINAL_PAYMENT("待付尾款", "WAITING_FOR_FINAL_PAYMENT"),
    WAITING_FOR_SHIPMENT("待发货", "WAITING_FOR_SHIPMENT"),
    IN_TRANSIT("运输中", "IN_TRANSIT"),
    DELIVERED("已送达", "DELIVERED"),
    COMPLETED("已完成", "COMPLETED"),
    
    // Cancel/refund statuses
    CANCELLING("取消中", "CANCELLING"),
    CANCELLED("已取消", "CANCELLED"),
    AFTER_SALE("售后中", "AFTER_SALE"),
    REFUNDED("已退款", "REFUNDED"),
    CLOSED("已关闭", "CLOSED"),
    
    // Merchant specific statuses
    PENDING_MERCHANT_ACCEPT("待接单", "PENDING_MERCHANT_ACCEPT"),
    MERCHANT_PROCESSING("商家处理中", "MERCHANT_PROCESSING"),
    
    // CS/Admin specific statuses
    REVIEW_PENDING("待审核", "REVIEW_PENDING"),
    EXCEPTION_HANDLING("异常处理", "EXCEPTION_HANDLING"),
    SYSTEM_ERROR("系统异常", "SYSTEM_ERROR");
    
    private final String displayName;
    private final String code;
    
    DerivedStatusView(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getCode() {
        return code;
    }
    
    /**
     * View types for different interfaces
     */
    public enum ViewType {
        USER_VIEW,      // Customer-facing mobile app/web
        MERCHANT_VIEW,  // Merchant management console
        CS_VIEW,        // Customer service console
        ADMIN_VIEW      // System admin console
    }
    
    /**
     * Context for deriving status
     */
    public static class DeriveContext {
        private final CoreFlowStatus coreFlowStatus;
        private final PaymentStatus paymentStatus;
        private final CancellationStatus cancellationStatus;
        private final AfterSaleStatus afterSaleStatus;
        private final ViewType viewType;
        private final boolean hasException;
        
        public DeriveContext(CoreFlowStatus coreFlowStatus, 
                           PaymentStatus paymentStatus,
                           CancellationStatus cancellationStatus, 
                           AfterSaleStatus afterSaleStatus,
                           ViewType viewType, 
                           boolean hasException) {
            this.coreFlowStatus = coreFlowStatus;
            this.paymentStatus = paymentStatus;
            this.cancellationStatus = cancellationStatus;
            this.afterSaleStatus = afterSaleStatus;
            this.viewType = viewType;
            this.hasException = hasException;
        }
        
        // Getters
        public CoreFlowStatus getCoreFlowStatus() { return coreFlowStatus; }
        public PaymentStatus getPaymentStatus() { return paymentStatus; }
        public CancellationStatus getCancellationStatus() { return cancellationStatus; }
        public AfterSaleStatus getAfterSaleStatus() { return afterSaleStatus; }
        public ViewType getViewType() { return viewType; }
        public boolean hasException() { return hasException; }
    }
}
