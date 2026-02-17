package com.github.spud.tinystore.order.domain.exception;

/**
 * 幂等服务不可用异常（Redis 故障等基础设施问题）
 *
 * 当 Redis 不可用时抛出此异常，应返回 503 Service Unavailable，
 * 通知客户端重试而非认为请求失败。
 */
public class IdempotencyServiceUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String operation;
    private final String idempotencyKey;

    /**
     * 构造幂等服务不可用异常
     *
     * @param operation 操作名称（tryAcquire/getCachedResponse/storeResponse）
     * @param idempotencyKey 幂等键
     * @param cause 原始异常（Redis 异常）
     */
    public IdempotencyServiceUnavailableException(String operation, String idempotencyKey, Throwable cause) {
        super("Idempotency service unavailable during " + operation + " (key=" + idempotencyKey + ")", cause);
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
    }

    public String getOperation() {
        return operation;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
