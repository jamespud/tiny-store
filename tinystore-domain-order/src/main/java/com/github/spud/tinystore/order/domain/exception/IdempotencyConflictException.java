package com.github.spud.tinystore.order.domain.exception;

/**
 * 幂等冲突异常（重复请求）
 */
public class IdempotencyConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String idempotencyKey;

    public IdempotencyConflictException(String message, String idempotencyKey) {
        super(message);
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
