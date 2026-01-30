package com.github.spud.tinystore.order.domain.exception;

/**
 * 领域冲突异常（非法状态迁移、并发冲突等）
 */
public class DomainConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String errorCode;

    public DomainConflictException(String message) {
        super(message);
        this.errorCode = "DOMAIN_CONFLICT";
    }

    public DomainConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DomainConflictException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "DOMAIN_CONFLICT";
    }

    public String getErrorCode() {
        return errorCode;
    }
}
