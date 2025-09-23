package com.github.spud.tinystore.order.interfaces.error;

import java.util.HashMap;
import java.util.Map;

/**
 * 订单业务异常
 *
 * @author Spud
 * @date 2025/8/16
 */
public class OrderBusinessException extends RuntimeException {

    private final OrderErrorCode errorCode;
    private final Map<String, Object> contextMap;

    public OrderBusinessException(OrderErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.contextMap = new HashMap<>();
    }

    public OrderBusinessException(OrderErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.contextMap = new HashMap<>();
    }

    public OrderBusinessException(OrderErrorCode errorCode, Map<String, Object> contextMap) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.contextMap = contextMap;
    }

    public OrderBusinessException(OrderErrorCode errorCode, String message,
                                  Map<String, Object> contextMap) {
        super(message);
        this.errorCode = errorCode;
        this.contextMap = contextMap;
    }


    public OrderErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getContextMap() {
        return contextMap;
    }

}
