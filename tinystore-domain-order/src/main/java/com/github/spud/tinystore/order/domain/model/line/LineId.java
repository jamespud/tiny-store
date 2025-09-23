package com.github.spud.tinystore.order.domain.model.line;

/**
 * 订单行标识符值对象
 * 
 * 强类型包装字符串，避免ID类型混淆
 *
 * @param value 行ID字符串值
 */
public record LineId(String value) {
    
    public LineId {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("LineId value cannot be null or empty");
        }
    }
    
    @Override
    public String toString() {
        return value;
    }
}