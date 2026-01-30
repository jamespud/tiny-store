package com.github.spud.tinystore.order.interfaces.error;

/**
 * 资源未找到异常
 */
public class NotFoundException extends RuntimeException {
    
    public NotFoundException(String message) {
        super(message);
    }
    
    public NotFoundException(String resourceType, String resourceId) {
        super(String.format("%s not found: %s", resourceType, resourceId));
    }
}
